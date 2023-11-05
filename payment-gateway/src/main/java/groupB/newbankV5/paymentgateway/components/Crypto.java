package groupB.newbankV5.paymentgateway.components;

import groupB.newbankV5.paymentgateway.connectors.BusinessIntegratorProxy;
import groupB.newbankV5.paymentgateway.connectors.dto.ApplicationDto;
import groupB.newbankV5.paymentgateway.entities.Application;
import groupB.newbankV5.paymentgateway.entities.ApplicationKeyPair;
import groupB.newbankV5.paymentgateway.entities.CreditCard;
import groupB.newbankV5.paymentgateway.exceptions.ApplicationNotFoundException;
import groupB.newbankV5.paymentgateway.exceptions.InvalidTokenException;
import groupB.newbankV5.paymentgateway.interfaces.IRSA;
import groupB.newbankV5.paymentgateway.repositories.ApplicationKeyPairRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public class Crypto implements IRSA {

    private static final Logger log = Logger.getLogger(Crypto.class.getName());

    private ApplicationKeyPairRepository applicationKeyPairRepository;
    private BusinessIntegratorProxy businessIntegratorProxy;

    @Autowired
    public Crypto(ApplicationKeyPairRepository applicationKeyPairRepository,BusinessIntegratorProxy businessIntegratorProxy) {
        this.applicationKeyPairRepository = applicationKeyPairRepository;
        this.businessIntegratorProxy=businessIntegratorProxy;

    }

    @Override
    public PublicKey getOrGenerateRSAPublicKey(String token) throws NoSuchAlgorithmException,ApplicationNotFoundException {
        ApplicationDto app;
        try {
            app = businessIntegratorProxy.validateToken(token);
        } catch (InvalidTokenException e) {
            throw new ApplicationNotFoundException("Application not found");
        }
        Application application = new Application(app.getName(),app.getEmail(),app.getUrl(),app.getDescription());
        application.setApiKey(app.getApiKey());
        application.setId(app.getId());
        app.setMerchant(app.getMerchant());
        Optional<ApplicationKeyPair> optApplicationKeyPair =
                applicationKeyPairRepository.findByApplicationName(app.getName());
        if(optApplicationKeyPair.isEmpty()) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            KeyPair pair = generator.generateKeyPair();
            ApplicationKeyPair applicationKeyPair = new ApplicationKeyPair();
            log.info("Generating RSA key pair for the application, the used key format is"
                    + pair.getPublic().getFormat());
            applicationKeyPair.setApplication(application);
            applicationKeyPair.setPublicKey(pair.getPublic());
            applicationKeyPair.setPrivateKey(pair.getPrivate());
            applicationKeyPairRepository.saveAndFlush(applicationKeyPair);
            return pair.getPublic();
        }
        return optApplicationKeyPair.get().getPublicKey();
    }

    @Override
    public CreditCard decryptPaymentRequestCreditCard(String encryptedData, ApplicationDto application)
            throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, ApplicationNotFoundException, InvalidKeySpecException {
        Optional<ApplicationKeyPair> optApplicationKeyPair =
                applicationKeyPairRepository.findByApplicationName(application.getName());
        if(optApplicationKeyPair.isEmpty()) {
            throw new ApplicationNotFoundException("Application not found");
        }

        KeyFactory kf = KeyFactory.getInstance("RSA");
        byte [] encoded = Base64.getDecoder().decode(Base64.getEncoder().encode(
                optApplicationKeyPair.get().getPrivateKey().getEncoded()
        ));
        PKCS8EncodedKeySpec keySpec1 = new PKCS8EncodedKeySpec(encoded);
        PrivateKey privateKey = kf.generatePrivate(keySpec1);

        Cipher decryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

        byte[] encryptedMessageBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedMessageBytes = decryptCipher.doFinal(encryptedMessageBytes);
        String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

//        Cipher cipher = Cipher.getInstance("RSA");
//        cipher.init(Cipher.DECRYPT_MODE, optApplicationKeyPair.get().getPrivateKey());
//        byte[] encryptedDataBytes = Base64.getDecoder().decode(encryptedData);
//        byte[] decryptedData = cipher.doFinal(encryptedDataBytes);
//        String decryptedMessage = new String(decryptedData, StandardCharsets.UTF_8);
        log.info("Decrypted message: " + decryptedMessage);

        String[] decryptedMessageArray = decryptedMessage.split(",");
        CreditCard creditCard = new CreditCard();
        creditCard.setCardNumber(decryptedMessageArray[0]);
        creditCard.setExpiryDate(decryptedMessageArray[1]);
        creditCard.setCvv(decryptedMessageArray[2]);
        return creditCard;
    }
}
