package groupB.newbankV5.paymentgateway.controllers;

import groupB.newbankV5.paymentgateway.controllers.dto.PaymentDto;
import groupB.newbankV5.paymentgateway.exceptions.ApplicationNotFoundException;
import groupB.newbankV5.paymentgateway.exceptions.CCNException;
import groupB.newbankV5.paymentgateway.exceptions.InvalidTokenException;
import groupB.newbankV5.paymentgateway.interfaces.ITransactionProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Logger;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
@RestController
@RequestMapping(path = TransactionerController.BASE_URI, produces = APPLICATION_JSON_VALUE)
public class TransactionerController {
    private static final Logger log = Logger.getLogger(TransactionerController.class.getName());
    public static final String BASE_URI = "/api/gateway";

    private final ITransactionProcessor transactionProcessor;

    @Autowired
    public TransactionerController(ITransactionProcessor transactionProcessor) {

        this.transactionProcessor = transactionProcessor;
    }

//    @PostMapping("/process")
//    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentDetailsDTO paymentDetails) {
//        log.info("Processing payment");
//        return ResponseEntity.status(HttpStatus.OK).body(transactionProcessor.authorizePayment(paymentDetails));
//
//    }

    @PostMapping("/authorize")
    public ResponseEntity<String> processPayment(@RequestBody PaymentDto paymentDetails) throws InvalidTokenException,
            ApplicationNotFoundException, CCNException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {
        log.info("Processing payment request in the Gateway");
        log.info("Payment details: " + paymentDetails.getCryptedCreditCard());
        log.info("Payment details: " + paymentDetails.getToken());
        log.info("Payment details: " + paymentDetails.getAmount());

        transactionProcessor.processPayment(paymentDetails.getToken(),
                paymentDetails.getAmount(),
                paymentDetails.getCryptedCreditCard());
        return ResponseEntity.status(204).body("");
    }

}
