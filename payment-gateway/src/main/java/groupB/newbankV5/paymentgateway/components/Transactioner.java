package groupB.newbankV5.paymentgateway.components;

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
import groupB.newbankV5.paymentgateway.interfaces.*;

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
public class Transactioner implements ITransactionProcessor, ITransactionFinder {

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
    public long getConfirmedTransaction(String token) throws InvalidTokenException, ApplicationNotFoundException {
        ApplicationDto application = businessIntegratorProxy.validateToken(token);
        Long merchantId = application.getMerchant().getId();
        long confirmedTransactionsCount = transactionRepository.findByStatus(TransactionStatus.CONFIRMED)
                .stream()
                .filter(transaction -> merchantId.equals(transaction.getMerchantId()))
                .count();
        return confirmedTransactionsCount;
    }
    @Override
    public long getAuthorizedTransaction(String token) throws InvalidTokenException, ApplicationNotFoundException {
        ApplicationDto application = businessIntegratorProxy.validateToken(token);
        Long merchantId = application.getMerchant().getId();
        long confirmedTransactionsCount = transactionRepository.findByStatus(TransactionStatus.AUTHORIZED)
                .stream()
                .filter(transaction -> merchantId.equals(transaction.getMerchantId()))
                .count();
        return confirmedTransactionsCount;
    }
    @Override
    public Transaction processPayment(String token, BigDecimal amount, String cryptedCreditCard) throws InvalidTokenException,
            ApplicationNotFoundException, CCNException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException,
            BadPaddingException, InvalidKeyException, InvalidKeySpecException {
        ApplicationDto application = businessIntegratorProxy.validateToken(token);
        MerchantDto merchant = application.getMerchant();
        CreditCard creditCard = rsa.decryptPaymentRequestCreditCard(cryptedCreditCard, application);
        log.info("\u001B[32mSending payment authorization request to CCN\u001B[0m");
        CcnResponseDto ccnResponseDto = creditCardNetworkProxy.authorizePayment(
                new PaymentDetailsDTO(creditCard.getCardNumber(), creditCard.getExpiryDate(), creditCard.getCvv(), amount)
        );
        if (!ccnResponseDto.isApproved()) {
            throw new CCNException("\u001B[31mPayment not authorized\u001B[0m");
        }
        Transaction transaction = new Transaction(merchant.getBankAccount(), ccnResponseDto.getAuthToken(), amount);
        transaction.setId(UUID.randomUUID());
        transaction.setExternal(true);
        transaction.setCreditCardType(ccnResponseDto.getCardType());
        transaction.setMerchantId(merchant.getId());
        CreditCard card = new CreditCard(creditCard.getCardNumber(), creditCard.getExpiryDate(), creditCard.getCvv());
        transaction.setCreditCard(card);
        transaction.setSender(new BankAccount(ccnResponseDto.getAccountIBAN(),ccnResponseDto.getAccountBIC()));
        transaction.setRecipient(merchant.getBankAccount());
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setBank(ccnResponseDto.getBankName());
        log.info("\u001B[32mPayment authorized\u001B[0m");

        transactionRepository.save(transaction);
        return transaction;
    }

    @Override
    public String confirmPayment(UUID transactionId ,String token) throws InvalidTokenException, ApplicationNotFoundException{
        ApplicationDto application = businessIntegratorProxy.validateToken(token);
        MerchantDto merchant = application.getMerchant();
        Transaction transaction = transactionRepository.findById(transactionId);
        if (transaction != null && transaction.getStatus().equals(TransactionStatus.AUTHORIZED)) {
            transaction.setStatus(TransactionStatus.CONFIRMED);
            transaction.setMerchantId(merchant.getId());
            transactionRepository.save(transaction);

            CreditCard usedCreditCard = transaction.getCreditCard();
            if(transaction.getBank().equals("NewBank")) {
                log.info("\u001B[34msend fund reservation request to NewBank\u001B[0m");
                paymentProcessor.reserveFunds(transaction);
            }
            else
                mockBankProxy.reserveFunds(transaction.getAmount(), usedCreditCard.getCardNumber(), usedCreditCard.getExpiryDate(), usedCreditCard.getCvv());
            transaction.setStatus(TransactionStatus.PENDING_SETTLEMENT);

            transactionRepository.save(transaction);
            log.info("\u001B[34mPublishing transaction to Kafka\u001B[0m");
            kafkaProducerService.sendMessage(transaction);
            return "Payment confirmed";
        }
        if (transaction == null)
            return "Transaction not found, try again";
        return "Payment already confirmed";

    }

}