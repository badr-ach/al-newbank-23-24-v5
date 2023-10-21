import { GatewayProxyService } from '../services/gateway-proxy/gateway-proxy.service';
import { MerchantDTO } from '../dto/merchant.dto';
import { ApplicationInfo } from '../dto/application-info.dto';
import { ApplicationDto } from '../dto/application.dto';
import { PaymentService } from './payment.service';
import { Logger, HttpException, HttpStatus } from '@nestjs/common';
import { InvalidMerchantInformationException } from '../exceptions/invalid-merchant-information.exception';
import { InvalidApplicationInformationException } from '../exceptions/invalid-application-information.exception';
export class IntegrationService {
  private readonly logger = new Logger(IntegrationService.name);

  constructor(
    private readonly gatewayProxyService: GatewayProxyService,
  ) {}

  validateMerchantInfo(name: string, email: string, iban: string, bic: string): void {
    if (!name || !email || !iban || !bic) {
      throw new InvalidMerchantInformationException("Name, email, IBAN, and BIC are required.");
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      throw new InvalidMerchantInformationException("Invalid email address.");
    }

    const ibanRegex = /^[A-Z]{2}\d{2}[A-Z0-9]{4}\d{7}([A-Z0-9]?){0,16}$/;
    if (!ibanRegex.test(iban)) {
        throw new InvalidMerchantInformationException("Invalid IBAN.");
    }

    const bicRegex = /^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$/;
    if (!bicRegex.test(bic)) {
      throw new InvalidMerchantInformationException("Invalid BIC.");
    }
  }

  async integrateMerchant(name: string, email: string, iban: string, bic: string): Promise<MerchantDTO> {
    try {
      this.validateMerchantInfo(name, email, iban, bic);

      const merchant = { name, email, bankAccount: { IBAN: iban, BIC: bic } };
      return await this.gatewayProxyService.integrateMerchant(merchant);

    } catch (error) {
      this.logger.error(`Error in integrating merchant: ${error.message}`);
      throw error;
    }
  }

  validateApplicationInfo(name: string, email: string, url: string, description: string, merchantId: string): void {
    if (!name || !email || !url || !description || !merchantId) {
      throw new InvalidApplicationInformationException("All fields are required.");
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      throw new InvalidApplicationInformationException("Invalid email address.");
    }
  }

  async integrateApplication(
    name: string,
    email: string,
    url: string,
    description: string,
    merchantId: string
  ): Promise<ApplicationDto> {
    try {
      this.validateApplicationInfo(name, email, url, description, merchantId);

      const integratedApplication = {
        name,
        email,
        url,
        description,
        merchantId,
      };

      return await this.gatewayProxyService.integrateApplication(integratedApplication);

    } catch (error) {
      this.logger.error(`Error in integrating application: ${error.message}`);
      throw error;
    }
  }

  async integrateService(application: ApplicationInfo): Promise<{ id: string, apiKey: string }> {
    try {
      const merchantIntegrationResult: MerchantDTO = await this.integrateMerchant(
        application.name, application.email, application.IBAN, application.BIC
      );

      this.logger.log(`Merchant integration successful for ${merchantIntegrationResult.name}. Merchant ID: ${merchantIntegrationResult.id}`);

      const applicationIntegrationResult: ApplicationDto = await this.integrateApplication(
        merchantIntegrationResult.name, merchantIntegrationResult.email,
        application.url, application.description, merchantIntegrationResult.id.toString()
      );

      const applicationId: string = applicationIntegrationResult.id.toString();
      this.logger.log(`Application integration successful for ${application.name}. Application ID: ${applicationId}`);

      return { id: applicationId, apiKey: applicationIntegrationResult.apiKey.toString() };

    } catch (error) {
      if (error instanceof InvalidMerchantInformationException) {
        this.logger.error(`Invalid Merchant Information: ${error.message}`);
      } else if (error instanceof InvalidApplicationInformationException) {
        this.logger.error(`Invalid Application Information: ${error.message}`);
      }
      throw new Error(`Error in integration service: ${error.message}`);
    }
  }
}


