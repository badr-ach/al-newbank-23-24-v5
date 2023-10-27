"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.GatewayProxyService = void 0;
const axios_1 = __importDefault(require("axios"));
const common_1 = require("@nestjs/common");
const merchant_already_exists_exception_1 = require("../../exceptions/merchant-already-exists.exception");
const application_not_found_exception_1 = require("../../exceptions/application-not-found.exception");
class GatewayProxyService {
    constructor(load_balancer_host) {
        this._gatewayPath = '/api/gateway/';
        this._gatewayBaseUrl = `http://${load_balancer_host}`;
    }
    integrateMerchant(merchant) {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                const url = `${this._gatewayBaseUrl}${this._gatewayPath}integration/merchants`;
                console.log(`Integration process started for merchant`);
                console.log(merchant);
                const response = yield axios_1.default.post(url, merchant);
                return response.data;
            }
            catch (error) {
                if (error.response && error.response.status === common_1.HttpStatus.CONFLICT) {
                    console.error(`Error integrating merchant : Merchant already exists`);
                    throw new merchant_already_exists_exception_1.MerchantAlreadyExists();
                }
                else {
                    const errorMessage = `Error integrating merchant. Unexpected error: ${error.message}`;
                    console.error(errorMessage);
                    throw new Error(errorMessage);
                }
            }
        });
    }
    integrateApplication(applicationIntegrationDto) {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                const url = `${this._gatewayBaseUrl}${this._gatewayPath}integration/applications`;
                console.log(`Integration process started for business`);
                const response = yield axios_1.default.post(url, applicationIntegrationDto);
                return response.data;
            }
            catch (error) {
                if (error.response && error.response.status === common_1.HttpStatus.CONFLICT) {
                    console.error(`Application already exists`);
                    throw new merchant_already_exists_exception_1.MerchantAlreadyExists();
                }
                else {
                    const errorMessage = `Error integrating business. Unexpected error: ${error.message}`;
                    console.error(errorMessage);
                    throw new Error(errorMessage);
                }
            }
        });
    }
    getAesKey(applicationId) {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                const response = yield axios_1.default.get(`${this._gatewayBaseUrl}${this._gatewayPath}integration/applications/${applicationId}/aeskey`);
                return response.data;
            }
            catch (error) {
                if (error.response && error.response.status === common_1.HttpStatus.NOT_FOUND) {
                    console.error(`Application not found`);
                    throw new application_not_found_exception_1.ApplicationNotFound();
                }
                else {
                    const errorMessage = `Error getting public key merchant: ${error.message}`;
                    console.error(errorMessage);
                    throw new Error(errorMessage);
                }
            }
        });
    }
    createApiKey(id) {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                console.log(`Generating API key process started for application ${id}`);
                const response = yield axios_1.default.post(`${this._gatewayBaseUrl}${this._gatewayPath}integration/applications/${id}/token`);
                this._apiKey = response.data;
                return response.data;
            }
            catch (error) {
                if (error.response && error.response.status === common_1.HttpStatus.NOT_FOUND) {
                    console.error(`Application not found`);
                    throw new application_not_found_exception_1.ApplicationNotFound();
                }
                else {
                    const errorMessage = `Error while generating API key: ${error.message}`;
                    console.error(errorMessage);
                    throw new Error(errorMessage);
                }
            }
        });
    }
    getPublicKey(applicationId) {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                const response = yield axios_1.default.get(`${this._gatewayBaseUrl}${this._gatewayPath}integration/applications/${applicationId}/publickey`);
                return response.data;
            }
            catch (error) {
                if (error.response && error.response.status === common_1.HttpStatus.NOT_FOUND) {
                    console.error(`Application not found`);
                    throw new application_not_found_exception_1.ApplicationNotFound();
                }
                else {
                    const errorMessage = `Error getting public key merchant: ${error.message}`;
                    console.error(errorMessage);
                    throw new Error(errorMessage);
                }
            }
        });
    }
    processPayment(encryptedCardInfo) {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                const httpOptions = {
                    headers: {
                        'Content-Type': 'application/json',
                    },
                };
                const response = yield axios_1.default.post(`${this._gatewayBaseUrl}${this._gatewayPath}authorize`, encryptedCardInfo, httpOptions);
                return response.data;
            }
            catch (error) {
                if (error.response && error.response.status === common_1.HttpStatus.NOT_FOUND) {
                    console.error(`Application not found`);
                    throw new application_not_found_exception_1.ApplicationNotFound();
                }
                else {
                    const errorMessage = `Error while processing payment: ${error.message}`;
                    console.error(errorMessage);
                    throw new Error(errorMessage);
                }
            }
        });
    }
}
exports.GatewayProxyService = GatewayProxyService;
