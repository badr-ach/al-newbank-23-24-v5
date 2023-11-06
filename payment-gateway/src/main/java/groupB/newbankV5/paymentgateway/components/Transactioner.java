package groupB.newbankV5.paymentgateway.components;

import com.sun.tools.jconsole.JConsoleContext;
import groupB.newbankV5.paymentgateway.config.KafkaProducerService;
import groupB.newbankV5.paymentgateway.connectors.BusinessIntegratorProxy;
import groupB.newbankV5.paymentgateway.connectors.CreditCardNetworkProxy;
import groupB.newbankV5.paymentgateway.connectors.MockBankProxy;
import groupB.newbankV5.paymentgateway.connectors.dto.ApplicationDto;
import groupB.newbankV5.paymentgateway.connectors.dto.CcnResponseDto;
import groupB.newbankV5.paymentgateway.connectors.dto.PaymentDetailsDTO;
import groupB.newbankV5.paymentgateway.controllers.dto.MerchantDto;
import groupB.newbankV5.paymentgateway.entities.*;
import groupB.newbankV5.paymentgateway.exceptions.ApplicationNotFoundException;
import groupB.newbankV5.paymentgateway.exceptions.CCNException;
import groupB.newbankV5.paymentgateway.exceptions.InvalidTokenException;
import groupB.newbankV5.paymentgateway.interfaces.IMockBank;
import groupB.newbankV5.paymentgateway.interfaces.IPaymentProcessor;
import groupB.newbankV5.paymentgateway.interfaces.IRSA;
import groupB.newbankV5.paymentgateway.interfaces.ITransactionProcessor;

import groupB.newbankV5.paymentgateway.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class Transactioner implements ITransactionProcessor {

    private static final Logger log = Logger.getLogger(Transactioner.class.getName());
    private final CreditCardNetworkProxy creditCardNetworkProxy;
    private final BusinessIntegratorProxy businessIntegratorProxy;
    private final IPaymentProcessor paymentProcessor;
    private final TransactionRepository transactionRepository;
    private final IMockBank mockBankProxy;
    private final IRSA rsa;

    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public Transactioner(
            CreditCardNetworkProxy creditCardNetworkProxy, IRSA rsa, BusinessIntegratorProxy businessIntegratorProxy, TransactionRepository transactionRepository, IPaymentProcessor paymentProcessor, MockBankProxy mockBankProxy, KafkaProducerService kafkaProducerService) {
        this.creditCardNetworkProxy = creditCardNetworkProxy;
        this.businessIntegratorProxy=businessIntegratorProxy;
        this.rsa = rsa;
        this.transactionRepository = transactionRepository;
        this.paymentProcessor = paymentProcessor;
        this.mockBankProxy = mockBankProxy;
        this.kafkaProducerService = kafkaProducerService;
    }


    @Override
    public Transaction processPayment(String token, BigDecimal amount, String cryptedCreditCard) throws InvalidTokenException,
            ApplicationNotFoundException, CCNException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException,
            BadPaddingException, InvalidKeyException, InvalidKeySpecException {
        ApplicationDto application = businessIntegratorProxy.validateToken(token);
        log.info("token validated");
        MerchantDto merchant = application.getMerchant();
        log.info("encrypted credit card: " + cryptedCreditCard);

        CreditCard creditCard = rsa.decryptPaymentRequestCreditCard(cryptedCreditCard, application);
        log.info("successfully decrypted credit card");

        CcnResponseDto ccnResponseDto = creditCardNetworkProxy.authorizePayment(
                new PaymentDetailsDTO(creditCard.getCardNumber(), creditCard.getExpiryDate(), creditCard.getCvv(), amount)
        );
        if (!ccnResponseDto.isApproved()) {
            throw new CCNException("Payment not authorized");
        }
        log.info(ccnResponseDto.getBankName());
        Transaction transaction = new Transaction(merchant.getBankAccount(), ccnResponseDto.getAuthToken(), amount);
        transaction.setId(UUID.randomUUID());
        transaction.setExternal(true);
        transaction.setCreditCardType(ccnResponseDto.getCardType());
        CreditCard card = new CreditCard(creditCard.getCardNumber(), creditCard.getExpiryDate(), creditCard.getCvv());
        transaction.setCreditCard(card);
        transaction.setSender(new BankAccount(ccnResponseDto.getAccountIBAN(),ccnResponseDto.getAccountBIC()));
        transaction.setRecipient(merchant.getBankAccount());
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setBank(ccnResponseDto.getBankName());

        transactionRepository.save(transaction);
        return transaction;
    }

    @Override
    public String confirmPayment(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction != null) {
            transaction.setStatus(TransactionStatus.CONFIRMED);
            transactionRepository.save(transaction);

            CreditCard usedCreditCard = transaction.getCreditCard();
            log.info("Confirming payment for transaction :"+transaction.getBank());
            if(transaction.getBank().equals("NewBank"))
                paymentProcessor.reserveFunds(transaction.getAmount(), usedCreditCard.getCardNumber(), usedCreditCard.getExpiryDate(), usedCreditCard.getCvv());
            else
                mockBankProxy.reserveFunds(transaction.getAmount(), usedCreditCard.getCardNumber(), usedCreditCard.getExpiryDate(), usedCreditCard.getCvv());
            transaction.setStatus(TransactionStatus.PENDING_SETTLEMENT);
            transactionRepository.save(transaction);
            kafkaProducerService.sendMessage(transaction);
            return "Payment confirmed";
        }
        return "Transaction not found, try again";
    }
}
