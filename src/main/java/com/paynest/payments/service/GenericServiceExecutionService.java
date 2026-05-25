package com.paynest.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.tenant.TenantTime;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.GenericIntegratorPayload;
import com.paynest.payments.dto.GenericServiceExecutionRequest;
import com.paynest.payments.dto.GenericServiceExecutionResponse;
import com.paynest.payments.dto.GenericServiceFinancialInfo;
import com.paynest.payments.dto.GenericServiceParty;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.entity.ServiceCatalog;
import com.paynest.payments.entity.ThirdPartyResponse;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.ServiceCatalogRepository;
import com.paynest.payments.repository.ThirdPartyResponseRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.service.PricingService;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GenericServiceExecutionService {

    private static final int TRANSACTION_ID_RANDOM_BOUND = 1_000_000;
    private static final String INTEGRATOR_NAME = "INTEGRATOR";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServiceCatalogRepository serviceCatalogRepository;
    private final TransactionsRepository transactionsRepository;
    private final ThirdPartyResponseRepository thirdPartyResponseRepository;
    private final GenericIntegratorClient genericIntegratorClient;
    private final PricingService pricingService;
    private final PaymentTransactionRecorderService paymentTransactionRecorderService;
    private final BalanceService balanceService;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final WalletCacheService walletCacheService;
    private final ObjectMapper objectMapper;

    public GenericServiceExecutionResponse executeFinancial(GenericServiceExecutionRequest request) {
        ServiceCatalog catalog = validateRequest(request, true);
        validateFinancialInfo(request);
        validateDebitorToken(request.getDebitor());

        String transactionId = generateTransactionId();
        boolean requiresConfirmation = Boolean.TRUE.equals(catalog.getRequiresConfirmation());
        String status = requiresConfirmation
                ? Constants.TRANSACTION_AMBIGUOUS
                : Constants.TRANSACTION_SUCCESS;
        PricingComputationResponse pricingInfo = calculatePricing(request, catalog);

        AccountIdentifier debitorIdentifier = getIdentifier(request.getDebitor(), "debitor");
        AccountIdentifier creditorIdentifier = getIdentifier(request.getCreditor(), "creditor");
        Account debitorAccount = getAccount(debitorIdentifier, "debitor");
        Account creditorAccount = getAccount(creditorIdentifier, "creditor");
        Wallet debitorWallet = getWallet(
                debitorIdentifier.getAccountId(),
                request.getDebitor(),
                request.getFinancialInfo().getCurrency(),
                "debitor"
        );
        Wallet creditorWallet = getWallet(
                creditorIdentifier.getAccountId(),
                request.getCreditor(),
                request.getFinancialInfo().getCurrency(),
                "creditor"
        );

        paymentTransactionRecorderService.recordTransaction(
                transactionId,
                request.getFinancialInfo().getAmount(),
                request.getRequestGateway().name(),
                catalog.getServiceCode(),
                request.getLanguage(),
                debitorIdentifier,
                creditorIdentifier,
                firstNonBlank(toName(request.getDebitor().getAccountType()), debitorAccount.getAccountType()),
                firstNonBlank(toName(request.getCreditor().getAccountType()), creditorAccount.getAccountType()),
                debitorWallet,
                creditorWallet,
                InitiatedBy.DEBITOR,
                toMap(request.getMetadata()),
                toMap(request.getAdditionalInfo()),
                request.getReferenceId(),
                catalog.getServiceName()
        );

        if (requiresConfirmation) {
            if (hasPricingAdjustments(pricingInfo)) {
                balanceService.parkWalletAmountInFicWithPricing(
                        debitorWallet,
                        creditorWallet,
                        request.getFinancialInfo().getAmount(),
                        catalog.getServiceCode(),
                        InitiatedBy.DEBITOR,
                        transactionId,
                        pricingInfo
                );
            } else {
                balanceService.parkWalletAmountInFic(
                        debitorWallet,
                        creditorWallet,
                        request.getFinancialInfo().getAmount(),
                        catalog.getServiceCode(),
                        InitiatedBy.DEBITOR,
                        transactionId
                );
            }
        } else {
            if (hasPricingAdjustments(pricingInfo)) {
                balanceService.transferWalletAmountWithPricing(
                        debitorWallet,
                        creditorWallet,
                        request.getFinancialInfo().getAmount(),
                        catalog.getServiceCode(),
                        InitiatedBy.DEBITOR,
                        transactionId,
                        pricingInfo
                );
            } else {
                balanceService.transferWalletAmount(
                        debitorWallet,
                        creditorWallet,
                        request.getFinancialInfo().getAmount(),
                        catalog.getServiceCode(),
                        InitiatedBy.DEBITOR,
                        transactionId
                );
            }
        }

        sendToIntegratorIfNeeded(catalog, request, transactionId, true, pricingInfo);
        refreshWalletCache(request);

        return buildResponse(request, catalog, transactionId, status);
    }

    public GenericServiceExecutionResponse executeNonFinancial(GenericServiceExecutionRequest request) {
        ServiceCatalog catalog = validateRequest(request, false);
        validateParty("debitor", request.getDebitor());
        validateDebitorToken(request.getDebitor());

        String transactionId = Boolean.TRUE.equals(catalog.getSendToIntegrator())
                ? generateTransactionId()
                : null;
        sendToIntegratorIfNeeded(catalog, request, transactionId, false, null);
        String status = Boolean.TRUE.equals(catalog.getRequiresConfirmation())
                ? Constants.TRANSACTION_AMBIGUOUS
                : Constants.TRANSACTION_SUCCESS;

        return buildResponse(request, catalog, transactionId, status);
    }

    private ServiceCatalog validateRequest(GenericServiceExecutionRequest request, boolean financial) {
        if (request == null) {
            throw invalidRequest("Request body is required");
        }
        if (isBlank(request.getServiceCode())) {
            throw invalidRequest("serviceCode is required");
        }
        if (request.getRequestGateway() == null) {
            throw invalidRequest("requestGateway is required");
        }
        if (isBlank(request.getLanguage())) {
            throw invalidRequest("language is required");
        }
        request.setLanguage(request.getLanguage().trim().toLowerCase());

        ServiceCatalog catalog = serviceCatalogRepository
                .findFirstByServiceCodeIgnoreCaseAndIsFinancialAndIsActiveTrue(request.getServiceCode(), financial)
                .orElseThrow(() -> invalidRequest("Invalid serviceCode for requested service type"));

        validateParty("debitor", request.getDebitor());
        validateParty("creditor", request.getCreditor());
        return catalog;
    }

    private void validateFinancialInfo(GenericServiceExecutionRequest request) {
        GenericServiceFinancialInfo financialInfo = request.getFinancialInfo();
        if (financialInfo == null) {
            throw invalidRequest("financialInfo is required");
        }
        if (financialInfo.getAmount() == null || financialInfo.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw invalidRequest("financialInfo.amount must be greater than zero");
        }
        if (isBlank(financialInfo.getCurrency())) {
            throw invalidRequest("financialInfo.currency is required");
        }
    }

    private void validateParty(String fieldName, Party party) {
        if (party == null) {
            throw invalidRequest(fieldName + " is required");
        }
        if (party.getIdentifier() == null) {
            throw invalidRequest(fieldName + ".identifier is required");
        }
        if (party.getIdentifier().getType() == null) {
            throw invalidRequest(fieldName + ".identifier.type is required");
        }
        if (isBlank(party.getIdentifier().getValue())) {
            throw invalidRequest(fieldName + ".identifier.value is required");
        }
        if (party.getWalletType() == null) {
            throw invalidRequest(fieldName + ".walletType is required");
        }
    }

    private void validateDebitorToken(Party debitor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isBlank(authentication.getName())) {
            throw new ApplicationException("TOKEN_REQUIRED", "Valid debitor token is required", HttpStatus.UNAUTHORIZED);
        }
        String debitorAccountId = getIdentifier(debitor, "debitor").getAccountId();
        if (!authentication.getName().equals(debitorAccountId)) {
            throw new ApplicationException(
                    "INVALID_DEBITOR_TOKEN",
                    "Token account must match debitor.identifier",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void sendToIntegratorIfNeeded(
            ServiceCatalog catalog,
            GenericServiceExecutionRequest request,
            String transactionId,
            boolean financial,
            PricingComputationResponse pricingInfo
    ) {
        if (!Boolean.TRUE.equals(catalog.getSendToIntegrator())) {
            return;
        }

        GenericIntegratorPayload payload = GenericIntegratorPayload.builder()
                .serviceCode(catalog.getServiceCode())
                .serviceName(catalog.getServiceName())
                .serviceCategory(catalog.getServiceCategory())
                .transactionType(catalog.getTransactionType())
                .serviceType(financial ? "FINANCIAL" : "NON_FINANCIAL")
                .referenceId(request.getReferenceId())
                .transactionId(transactionId)
                .debitor(toGenericParty(request.getDebitor(), "debitor", request.getFinancialInfo()))
                .creditor(toGenericParty(request.getCreditor(), "creditor", request.getFinancialInfo()))
                .partnerData(request.getPartnerData())
                .financialInfo(request.getFinancialInfo())
                .pricingInfo(pricingInfo)
                .metadata(request.getMetadata())
                .additionalInfo(request.getAdditionalInfo())
                .build();

        recordIntegratorRequest(payload);
        sendToIntegrator(payload, catalog.getIntegratorCallMode());
    }

    private void sendToIntegrator(GenericIntegratorPayload payload, String integratorCallMode) {
        String transactionId = payload.getTransactionId();
        try {
            JsonNode response = genericIntegratorClient.send(payload, integratorCallMode);
            if (isFailureResponse(response)) {
                updateIntegratorResponse(transactionId, "FAILED", response, "Integrator returned failure");
                throw integratorFailure("Integrator returned failure");
            }
            updateIntegratorResponse(transactionId, "SUCCESS", response, null);
        } catch (ApplicationException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            String status = isTimeout(ex) ? "TIMEOUT" : "FAILED";
            updateIntegratorResponse(transactionId, status, null, ex.getMessage());
            throw integratorFailure(isTimeout(ex) ? "Integrator call timed out" : "Integrator call failed");
        } catch (RestClientResponseException ex) {
            JsonNode errorBody = parseJson(ex.getResponseBodyAsString());
            updateIntegratorResponse(transactionId, "FAILED", errorBody, ex.getMessage());
            throw integratorFailure("Integrator returned HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            updateIntegratorResponse(transactionId, "FAILED", null, ex.getMessage());
            throw integratorFailure("Integrator call failed");
        }
    }

    private void recordIntegratorRequest(GenericIntegratorPayload payload) {
        ThirdPartyResponse record = thirdPartyResponseRepository.findByTransactionId(payload.getTransactionId())
                .orElseGet(ThirdPartyResponse::new);
        record.setTransactionId(payload.getTransactionId());
        record.setServiceCode(payload.getServiceCode());
        record.setIntegratorName(INTEGRATOR_NAME);
        if (record.getRequestBody() == null || record.getRequestBody().isNull()) {
            record.setRequestBody(objectMapper.valueToTree(payload));
        }
        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("PENDING");
        }
        thirdPartyResponseRepository.save(record);
    }

    private void updateIntegratorResponse(
            String transactionId,
            String status,
            JsonNode responseBody,
            String errorMessage
    ) {
        ThirdPartyResponse record = thirdPartyResponseRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalStateException("Third party response record not found: " + transactionId));
        record.setStatus(status);
        record.setResponseBody(responseBody);
        record.setErrorMessage(errorMessage);
        thirdPartyResponseRepository.save(record);
    }

    private GenericServiceExecutionResponse buildResponse(
            GenericServiceExecutionRequest request,
            ServiceCatalog catalog,
            String transactionId,
            String status
    ) {
        GenericServiceFinancialInfo financialInfo = request.getFinancialInfo();
        return GenericServiceExecutionResponse.builder()
                .serviceCode(catalog.getServiceCode())
                .serviceName(catalog.getServiceName())
                .referenceId(request.getReferenceId())
                .transactionId(transactionId)
                .status(status)
                .amount(financialInfo != null ? financialInfo.getAmount() : null)
                .currency(financialInfo != null ? financialInfo.getCurrency() : null)
                .build();
    }

    private PricingComputationResponse calculatePricing(GenericServiceExecutionRequest request, ServiceCatalog catalog) {
        GenericServiceFinancialInfo financialInfo = request.getFinancialInfo();
        return pricingService.calculatePricingAmountsForAccounts(
                catalog.getServiceCode(),
                financialInfo.getCurrency(),
                financialInfo.getAmount(),
                getIdentifier(request.getDebitor(), "debitor").getAccountId(),
                getIdentifier(request.getCreditor(), "creditor").getAccountId()
        );
    }

    private void refreshWalletCache(GenericServiceExecutionRequest request) {
        Set<String> accountIds = new LinkedHashSet<>();
        addResolvedAccountId(accountIds, request.getDebitor(), "debitor");
        addResolvedAccountId(accountIds, request.getCreditor(), "creditor");
        accountIds.forEach(walletCacheService::refreshAccountWallets);
    }

    private boolean hasPricingAdjustments(PricingComputationResponse pricingInfo) {
        return positive(pricingInfo != null ? pricingInfo.getServiceChargeAmount() : null)
                || positive(pricingInfo != null ? pricingInfo.getCommissionAmount() : null)
                || positive(pricingInfo != null ? pricingInfo.getDiscountAmount() : null)
                || positive(pricingInfo != null ? pricingInfo.getCashbackAmount() : null);
    }

    private boolean positive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private String generateTransactionId() {
       return IdGenerator.generateTransactionId("GT");
    }

    private ApplicationException invalidRequest(String message) {
        return new ApplicationException("INVALID_REQUEST", message);
    }

    private ApplicationException integratorFailure(String message) {
        return new ApplicationException("INTEGRATOR_FAILURE", message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private AccountIdentifier getIdentifier(Party party, String role) {
        Identifier identifier = party.getIdentifier();
        String identifierType = resolveIdentifierTypeForLookup(identifier.getType());

        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        identifierType,
                        identifier.getValue(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> invalidRequest(role + ".identifier is invalid or inactive"));
    }

    private String resolveIdentifierTypeForLookup(IdentifierType identifierType) {
        if (identifierType == IdentifierType.MSISDN) {
            return IdentifierType.MOBILE.name();
        }
        return identifierType.name();
    }

    private Account getAccount(AccountIdentifier identifier, String role) {
        return accountRepository.findByAccountIdAndStatus(identifier.getAccountId(), Constants.ACCOUNT_STATUS_ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> invalidRequest(role + ".identifier account is invalid or inactive"));
    }

    private Wallet getWallet(String accountId, Party party, String currency, String role) {
        return walletRepository
                .findByAccountIdAndCurrencyAndWalletType(accountId, currency, party.getWalletType().name())
                .orElseThrow(() -> invalidRequest(role + " wallet not found"));
    }

    private void addResolvedAccountId(Set<String> accountIds, Party party, String role) {
        if (party != null && party.getIdentifier() != null && party.getIdentifier().getType() != null
                && !isBlank(party.getIdentifier().getValue())) {
            accountIds.add(getIdentifier(party, role).getAccountId());
        }
    }

    private String toName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private GenericServiceParty toGenericParty(
            Party party,
            String role,
            GenericServiceFinancialInfo financialInfo
    ) {
        AccountIdentifier identifier = getIdentifier(party, role);
        Account account = getAccount(identifier, role);
        GenericServiceParty genericParty = new GenericServiceParty();
        genericParty.setAccountId(account.getAccountId());
        genericParty.setAccountCode(account.getAccountCode());
        genericParty.setAccountType(account.getAccountType());
        genericParty.setWalletType(party.getWalletType().name());
        genericParty.setCurrency(financialInfo != null ? financialInfo.getCurrency() : null);
        return genericParty;
    }

    private Map<String, Object> toMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        return objectMapper.convertValue(jsonNode, Map.class);
    }

    private boolean isFailureResponse(JsonNode response) {
        if (response == null || response.isNull()) {
            return false;
        }
        return isFailureValue(response, "status")
                || isFailureValue(response, "responseStatus")
                || isFailureValue(response, "result")
                || isFailureValue(response, "code");
    }

    private boolean isFailureValue(JsonNode response, String fieldName) {
        JsonNode value = response.get(fieldName);
        if (value == null || value.isNull()) {
            return false;
        }
        String normalized = value.asText("").trim().toUpperCase();
        return "FAILURE".equals(normalized)
                || "FAILED".equals(normalized)
                || "ERROR".equals(normalized)
                || "REJECTED".equals(normalized);
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return true;
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
