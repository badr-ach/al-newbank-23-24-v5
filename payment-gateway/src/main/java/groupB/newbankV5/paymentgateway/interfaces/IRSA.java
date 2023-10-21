package groupB.newbankV5.paymentgateway.interfaces;

import groupB.newbankV5.paymentgateway.controllers.dto.PaymentDto;
import groupB.newbankV5.paymentgateway.entities.Application;
import groupB.newbankV5.paymentgateway.entities.CreditCard;
import groupB.newbankV5.paymentgateway.exceptions.ApplicationNotFoundException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public interface IRSA {
     SecretKey getOrGenerateAESKey(Application application) throws NoSuchAlgorithmException;
     String encryptCreditCard(CreditCard creditCard, SecretKey aesKey) throws NoSuchPaddingException,
             NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException;
     CreditCard decryptPaymentRequestCreditCard(String encryptedData, Application application )  throws NoSuchPaddingException,
             NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException;
}
