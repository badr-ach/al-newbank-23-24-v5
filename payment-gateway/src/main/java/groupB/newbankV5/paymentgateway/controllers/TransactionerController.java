package groupB.newbankV5.paymentgateway.controllers;

import groupB.newbankV5.paymentgateway.controllers.dto.AuthorizeDto;
import groupB.newbankV5.paymentgateway.controllers.dto.PaymentDto;
import groupB.newbankV5.paymentgateway.exceptions.ApplicationNotFoundException;
import groupB.newbankV5.paymentgateway.exceptions.CCNException;
import groupB.newbankV5.paymentgateway.exceptions.InvalidTokenException;
import groupB.newbankV5.paymentgateway.interfaces.IRSA;
import groupB.newbankV5.paymentgateway.interfaces.ITransactionProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.websocket.server.PathParam;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
@RestController
@RequestMapping(path = TransactionerController.BASE_URI, produces = APPLICATION_JSON_VALUE)
public class TransactionerController {
    private static final Logger log = Logger.getLogger(TransactionerController.class.getName());
    public static final String BASE_URI = "/api/gateway";
    private final IRSA crypto;
    private final ITransactionProcessor transactionProcessor;

    @Autowired
    public TransactionerController(ITransactionProcessor transactionProcessor,IRSA crypto) {
        this.transactionProcessor = transactionProcessor;
        this.crypto=crypto;
    }

//    @PostMapping("/process")
//    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentDetailsDTO paymentDetails) {
//        log.info("Processing payment");
//        return ResponseEntity.status(HttpStatus.OK).body(transactionProcessor.authorizePayment(paymentDetails));
//
//    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizeDto> processPayment(@RequestBody PaymentDto paymentDetails, @RequestHeader("Authorization") String token ) throws InvalidTokenException,
            ApplicationNotFoundException, CCNException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {
        log.info("Processing payment request in the Gateway");
        log.info("Payment details: " + paymentDetails.getEncryptedCard());
        log.info("Payment details: " + paymentDetails.getAmount());
        UUID transactionId = transactionProcessor.processPayment(token,
                paymentDetails.getAmount(),
                paymentDetails.getEncryptedCard()).getId();
        AuthorizeDto authorizeDto = new AuthorizeDto(transactionId);
        return ResponseEntity.status(204).body(authorizeDto);
    }
    @GetMapping("/applications/publickey")
    public ResponseEntity<String> getAesKey(@RequestHeader("Authorization") String token) throws NoSuchAlgorithmException, ApplicationNotFoundException {
        PublicKey publicKey = crypto.getOrGenerateRSAPublicKey(token);
        return ResponseEntity.ok().body(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }

    @PostMapping("/confirmPayment/transactionId")
    public ResponseEntity<String> confirmPayment(@PathParam("transactionId") UUID transactionId) {
        log.info("Confirming payment");
        return ResponseEntity.status(204).body(transactionProcessor.confirmPayment(transactionId));
    }


}
