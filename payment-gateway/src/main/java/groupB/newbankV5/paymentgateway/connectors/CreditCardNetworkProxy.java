package groupB.newbankV5.paymentgateway.connectors;

import groupB.newbankV5.paymentgateway.connectors.dto.CcnResponseDto;
import groupB.newbankV5.paymentgateway.connectors.dto.CreditCardInformationDto;
import groupB.newbankV5.paymentgateway.connectors.dto.PaymentDetailsDTO;
import groupB.newbankV5.paymentgateway.interfaces.ICreditCardNetwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.logging.Logger;

@Component
public class CreditCardNetworkProxy implements ICreditCardNetwork {
    private static final Logger log = Logger.getLogger(CreditCardNetworkProxy.class.getName());


    @Value("${ccn.host.baseurl:}")
    private String ccnHostandPort;

    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public CcnResponseDto authorizePayment(PaymentDetailsDTO paymentDetailsDTO) {
        log.info("Authorizing payment");

        CreditCardInformationDto creditCardInformationDto = new CreditCardInformationDto();
        creditCardInformationDto.setCardNumber(paymentDetailsDTO.getCardNumber());
        creditCardInformationDto.setExpirationDate(paymentDetailsDTO.getExpirationDate());
        creditCardInformationDto.setCvv(paymentDetailsDTO.getCvv());
        try {
            return restTemplate.postForEntity(ccnHostandPort + "/api/payment/authorize",
                    creditCardInformationDto, CcnResponseDto.class).getBody();
        } catch (Exception e) {
            log.info("Error: " + e.getMessage());
            return new CcnResponseDto(false, "Error: " + e.getMessage());
        }
    }

}
