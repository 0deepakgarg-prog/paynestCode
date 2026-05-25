package com.paynest.payments.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.config.tenant.TenantTime;
import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.enums.AccountType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.BillPayPaymentRequest;
import com.paynest.payments.dto.BillPayPaymentResponse;
import com.paynest.payments.dto.GenericIntegratorPayload;
import com.paynest.payments.dto.GenericServiceFinancialInfo;
import com.paynest.payments.dto.GenericServiceParty;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.repository.ServiceCatalogRepository;
import com.paynest.payments.validation.BasePaymentRequestValidator;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.service.PricingService;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.config.security.JWTUtils;
import com.paynest.users.service.AuthService;
import com.paynest.config.tenant.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class BillPayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BillPayPaymentService.class);
    private static final String OPERATION_NAME = "BILLPAY";
    private static final String TRANSACTION_PREFIX = "BP";
    private static final AccountType DEBITOR_ACCOUNT_TYPE = AccountType.SUBSCRIBER;
    private static final AccountType CREDITOR_ACCOUNT_TYPE = AccountType.BILLER;

    private final BasePaymentRequestValidator basePaymentRequestValidator;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final PropertyReader propertyReader;
    private final PaymentTransactionRecorderService paymentTransactionRecorderService;
    private final BalanceService balanceService;
    private final AuthService authService;
    private final BillPaymentStatusService billPaymentStatusService;
    private final PricingService pricingService;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final BillPayIntegratorSettlementService billPayIntegratorSettlementService;
    private final ObjectMapper objectMapper;

    public BillPayPaymentService(
            BasePaymentRequestValidator basePaymentRequestValidator,
            AccountIdentifierRepository accountIdentifierRepository,
            AccountRepository accountRepository,
            WalletRepository walletRepository,
            PropertyReader propertyReader,
            PaymentTransactionRecorderService paymentTransactionRecorderService,
            BalanceService balanceService,
            AuthService authService,
            BillPaymentStatusService billPaymentStatusService,
            PricingService pricingService,
            ServiceCatalogRepository serviceCatalogRepository,
            BillPayIntegratorSettlementService billPayIntegratorSettlementService,
            ObjectMapper objectMapper
    ) {
        this.basePaymentRequestValidator = basePaymentRequestValidator;
        this.accountIdentifierRepository = accountIdentifierRepository;
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
        this.propertyReader = propertyReader;
        this.paymentTransactionRecorderService = paymentTransactionRecorderService;
        this.balanceService = balanceService;
        this.authService = authService;
        this.billPaymentStatusService = billPaymentStatusService;
        this.pricingService = pricingService;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.billPayIntegratorSettlementService = billPayIntegratorSettlementService;
        this.objectMapper = objectMapper;
    }

    public BillPayPaymentResponse processPayment(BillPayPaymentRequest request, boolean validateJWT) {
        log.info("Processing {} payment request. traceId={}", OPERATION_NAME, TraceContext.getTraceId());
        basePaymentRequestValidator.validate(request);
        normalizeRequest(request);
        String currency = request.getTransaction().getCurrency();

        validateParty(request.getDebitor(), InitiatedBy.DEBITOR, DEBITOR_ACCOUNT_TYPE);
        validateParty(request.getCreditor(), InitiatedBy.CREDITOR, CREDITOR_ACCOUNT_TYPE);
        validateCreditorIdentifierType(request.getCreditor());
        validateMatchingWalletTypes(request.getDebitor(), request.getCreditor());

        AccountIdentifier debitorIdentifier = getIdentifier(request.getDebitor());
        AccountIdentifier creditorIdentifier = getIdentifier(request.getCreditor());
        validateDifferentAccounts(debitorIdentifier, creditorIdentifier);
        validateJwtAccess(
                validateJWT,
                debitorIdentifier,
                request.getDebitor().getAuthentication(),
                DEBITOR_ACCOUNT_TYPE
        );

        Account debitorAccount = getAccount(debitorIdentifier);
        Account creditorAccount = getAccount(creditorIdentifier);

        validateAccountType(debitorAccount, request.getDebitor().getAccountType(), InitiatedBy.DEBITOR.name());
        validateAccountType(creditorAccount, request.getCreditor().getAccountType(), InitiatedBy.CREDITOR.name());
        validateInitiator(request.getInitiatedBy());

        Authentication debitorAuthentication = request.getDebitor().getAuthentication();
        authService.validateAuthentication(
                debitorAuthentication.getValue(),
                debitorAuthentication.getType(),
                debitorIdentifier
        );

        Wallet debitorWallet = getWallet(
                debitorAccount.getAccountId(),
                request.getDebitor(),
                currency,
                InitiatedBy.DEBITOR.name()
        );
        Wallet creditorWallet = getWallet(
                creditorAccount.getAccountId(),
                request.getCreditor(),
                currency,
                InitiatedBy.CREDITOR.name()
        );
        PricingComputationResponse pricingComputation = pricingService.calculatePricingAmounts(request);

        String transactionId = IdGenerator.generateTransactionId(
                TRANSACTION_PREFIX,
                getRequiredServerInstance()
        );

        try {
            createTransactionRecord(
                    transactionId,
                    request,
                    debitorIdentifier,
                    creditorIdentifier,
                    debitorAccount.getAccountType(),
                    creditorAccount.getAccountType(),
                    debitorWallet,
                    creditorWallet
            );

            billPaymentStatusService.createPendingStatus(
                    transactionId,
                    TraceContext.getTraceId(),
                    debitorAccount.getAccountId(),
                    creditorAccount.getAccountId()
            );

            if (hasPricingAdjustments(pricingComputation)) {
                balanceService.parkWalletAmountInFicWithPricing(
                        debitorWallet,
                        creditorWallet,
                        request.getTransaction().getAmount(),
                        request.getOperationType(),
                        request.getInitiatedBy(),
                        transactionId,
                        pricingComputation
                );
            } else {
                balanceService.parkWalletAmountInFic(
                        debitorWallet,
                        creditorWallet,
                        request.getTransaction().getAmount(),
                        request.getOperationType(),
                        request.getInitiatedBy(),
                        transactionId
                );
            }

            sendToIntegratorIfConfigured(
                    request,
                    transactionId,
                    debitorAccount,
                    creditorAccount,
                    debitorWallet,
                    creditorWallet,
                    pricingComputation
            );
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(transactionId);
        }

        return buildSuccessResponse(request, transactionId);
    }

    private BigDecimal getServiceChargeAmount(PricingComputationResponse pricingComputation) {
        if (pricingComputation == null || pricingComputation.getServiceChargeAmount() == null) {
            return BigDecimal.ZERO;
        }
        return pricingComputation.getServiceChargeAmount();
    }

    private BigDecimal getDiscountAmount(PricingComputationResponse pricingComputation) {
        if (pricingComputation == null || pricingComputation.getDiscountAmount() == null) {
            return BigDecimal.ZERO;
        }
        return pricingComputation.getDiscountAmount();
    }

    private BigDecimal getCashbackAmount(PricingComputationResponse pricingComputation) {
        if (pricingComputation == null || pricingComputation.getCashbackAmount() == null) {
            return BigDecimal.ZERO;
        }
        return pricingComputation.getCashbackAmount();
    }

    private boolean hasPricingAdjustments(PricingComputationResponse pricingComputation) {
        return getServiceChargeAmount(pricingComputation).compareTo(BigDecimal.ZERO) > 0
                || getDiscountAmount(pricingComputation).compareTo(BigDecimal.ZERO) > 0
                || getCashbackAmount(pricingComputation).compareTo(BigDecimal.ZERO) > 0;
    }

    private BillPayPaymentResponse buildSuccessResponse(BillPayPaymentRequest request, String transactionId) {
        return BillPayPaymentResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(request.getOperationType())
                .code("PAYMENT_SUCCESS")
                .message("Bill payment successful and pending settlement")
                .timestamp(TenantTime.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .currency(request.getTransaction().getCurrency())
                .billStatus(BillPaymentStatus.PENDING)
                .build();
    }

    private void sendToIntegratorIfConfigured(
            BillPayPaymentRequest request,
            String transactionId,
            Account debitorAccount,
            Account creditorAccount,
            Wallet debitorWallet,
            Wallet creditorWallet,
            PricingComputationResponse pricingComputation
    ) {
        serviceCatalogRepository.findFirstByServiceCodeIgnoreCaseAndIsActiveTrue(OPERATION_NAME)
                .filter(serviceCatalog -> Boolean.TRUE.equals(serviceCatalog.getSendToIntegrator()))
                .ifPresent(serviceCatalog -> {
                    GenericIntegratorPayload payload = GenericIntegratorPayload.builder()
                                .serviceCode(serviceCatalog.getServiceCode())
                                .serviceName(serviceCatalog.getServiceName())
                                .serviceCategory(serviceCatalog.getServiceCategory())
                                .transactionType(serviceCatalog.getTransactionType())
                                .serviceType("FINANCIAL")
                                .referenceId(request.getPaymentReference())
                                .transactionId(transactionId)
                                .debitor(toGenericParty(debitorAccount, debitorWallet, request.getDebitor()))
                                .creditor(toGenericParty(creditorAccount, creditorWallet, request.getCreditor()))
                                .partnerData(objectMapper.valueToTree(request.getPartnerData()))
                                .financialInfo(toGenericFinancialInfo(request))
                                .pricingInfo(pricingComputation)
                                .metadata(objectMapper.valueToTree(request.getMetadata()))
                                .build();
                    boolean confirmationRequired = Boolean.TRUE.equals(serviceCatalog.getRequiresConfirmation());
                    registerAfterCommitIntegratorCall(
                            payload,
                            confirmationRequired,
                            serviceCatalog.getIntegratorCallMode()
                    );
                });
    }

    private void registerAfterCommitIntegratorCall(
            GenericIntegratorPayload payload,
            boolean confirmationRequired,
            String integratorCallMode
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callIntegrator(payload, confirmationRequired, integratorCallMode);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callIntegrator(payload, confirmationRequired, integratorCallMode);
            }
        });
    }

    private void callIntegrator(
            GenericIntegratorPayload payload,
            boolean confirmationRequired,
            String integratorCallMode
    ) {
        billPayIntegratorSettlementService.callIntegratorAndSettle(
                payload,
                confirmationRequired,
                integratorCallMode
        );
    }

    private GenericServiceParty toGenericParty(Account account, Wallet wallet, Party requestParty) {
        GenericServiceParty party = new GenericServiceParty();
        party.setAccountId(account.getAccountId());
        party.setAccountCode(account.getAccountCode());
        party.setAccountType(account.getAccountType());
        if (requestParty != null && requestParty.getWalletType() != null) {
            party.setWalletType(requestParty.getWalletType().name());
        }
        if (wallet != null) {
            party.setCurrency(wallet.getCurrency());
        }
        return party;
    }

    private GenericServiceFinancialInfo toGenericFinancialInfo(BillPayPaymentRequest request) {
        GenericServiceFinancialInfo financialInfo = new GenericServiceFinancialInfo();
        financialInfo.setAmount(request.getTransaction().getAmount());
        financialInfo.setCurrency(request.getTransaction().getCurrency());
        return financialInfo;
    }

    private void validateParty(Party party, InitiatedBy role, AccountType expectedType) {
        if (party.getAccountType() != expectedType) {
            throw new ApplicationException(
                    role == InitiatedBy.DEBITOR
                            ? PaymentErrorCode.INVALID_DEBITOR_USER_TYPE
                            : PaymentErrorCode.INVALID_CREDITOR_USER_TYPE,
                    null,
                    Map.of(
                            "role", role.name(),
                            "accountType", String.valueOf(party.getAccountType()),
                            "operationType", OPERATION_NAME
                    )
            );
        }
    }

    private void validateCreditorIdentifierType(Party creditor) {
        IdentifierType identifierType = creditor.getIdentifier().getType();
        if (identifierType != IdentifierType.LOGINID
                && identifierType != IdentifierType.MSISDN
                && identifierType != IdentifierType.MOBILE) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CREDITOR_IDENTIFIER_TYPE,
                    null,
                    Map.of(
                            "operationType", OPERATION_NAME,
                            "accountType", CREDITOR_ACCOUNT_TYPE.name(),
                            "allowedTypes", "MOBILE, MSISDN, LOGINID"
                    )
            );
        }
    }

    private void validateJwtAccess(
            boolean validateJWT,
            AccountIdentifier debitorIdentifier,
            Authentication requestedAuthentication,
            AccountType expectedAccountType
    ) {
        if (!validateJWT) {
            return;
        }

        String currentAccountId;
        String currentAccountType;
        String currentAuthType;
        try {
            currentAccountId = JWTUtils.getCurrentAccountId();
            currentAccountType = JWTUtils.getCurrentAccountType();
            currentAuthType = JWTUtils.getCurrentAuthType();
        } catch (Exception ex) {
            throw new ApplicationException(PaymentErrorCode.UNAUTHORIZED);
        }

        if (currentAccountId == null || currentAccountId.isBlank()
                || currentAccountType == null || currentAccountType.isBlank()
                || currentAuthType == null || currentAuthType.isBlank()) {
            throw new ApplicationException(PaymentErrorCode.UNAUTHORIZED);
        }

        if (!currentAccountId.equalsIgnoreCase(debitorIdentifier.getAccountId())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRIVILEGES,
                    null,
                    Map.of("operationType", OPERATION_NAME)
            );
        }

        if (!expectedAccountType.name().equalsIgnoreCase(currentAccountType)) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRIVILEGES,
                    null,
                    Map.of(
                            "operationType", OPERATION_NAME,
                            "expectedScope", expectedAccountType.name(),
                            "actualScope", currentAccountType
                    )
            );
        }

        if (!requestedAuthentication.getType().name().equalsIgnoreCase(currentAuthType)) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_AUTH_TYPE,
                    null,
                    Map.of(
                            "operationType", OPERATION_NAME,
                            "expectedAuthType", requestedAuthentication.getType().name(),
                            "actualAuthType", currentAuthType
                    )
            );
        }
    }

    private void validateAccountType(Account account, AccountType expectedType, String role) {
        if (!account.getAccountType().equalsIgnoreCase(expectedType.name())) {
            throw new ApplicationException(
                    InitiatedBy.DEBITOR.name().equals(role)
                            ? PaymentErrorCode.INVALID_DEBITOR_ACCOUNT_TYPE
                            : PaymentErrorCode.INVALID_CREDITOR_ACCOUNT_TYPE,
                    null,
                    Map.of(
                            "role", role,
                            "expectedType", expectedType.name(),
                            "actualType", account.getAccountType()
                    )
            );
        }
    }

    private void validateInitiator(InitiatedBy initiatedBy) {
        if (InitiatedBy.CREDITOR.equals(initiatedBy)) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_INITIATOR,
                    null,
                    Map.of("initiatedBy", initiatedBy.name())
            );
        }
    }

    private void normalizeRequest(BillPayPaymentRequest request) {
        request.getTransaction().setCurrency(
                request.getTransaction().getCurrency().trim().toUpperCase(Locale.ROOT)
        );
        request.setPreferredLang(request.getPreferredLang().trim().toLowerCase(Locale.ROOT));
        request.setPaymentReference(normalizeOptionalText(request.getPaymentReference()));
        request.setComments(normalizeOptionalText(request.getComments()));
        request.getDebitor().getIdentifier().setValue(request.getDebitor().getIdentifier().getValue().trim());
        request.getCreditor().getIdentifier().setValue(request.getCreditor().getIdentifier().getValue().trim());
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateMatchingWalletTypes(Party debitor, Party creditor) {
        if (debitor.getWalletType() != creditor.getWalletType()) {
            throw new ApplicationException(
                    PaymentErrorCode.CROSS_WALLET_TRANSFER_NOT_ALLOWED,
                    null,
                    Map.of(
                            "operationType", OPERATION_NAME,
                            "debitorWalletType", debitor.getWalletType().name(),
                            "creditorWalletType", creditor.getWalletType().name()
                    )
            );
        }
    }

    private void validateDifferentAccounts(AccountIdentifier debitorIdentifier, AccountIdentifier creditorIdentifier) {
        if (debitorIdentifier.getAccountId().equalsIgnoreCase(creditorIdentifier.getAccountId())) {
            throw new ApplicationException(PaymentErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }
    }

    private AccountIdentifier getIdentifier(Party party) {
        Identifier identifier = party.getIdentifier();
        String identifierType = resolveIdentifierTypeForLookup(identifier.getType());

        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        identifierType,
                        identifier.getValue(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() ->
                        new ApplicationException(
                                PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND,
                                null,
                                Map.of("identifierValue", identifier.getValue())
                        ));
    }

    private String resolveIdentifierTypeForLookup(IdentifierType identifierType) {
        if (identifierType == IdentifierType.MSISDN) {
            return IdentifierType.MOBILE.name();
        }
        return identifierType.name();
    }

    private Account getAccount(AccountIdentifier identifier) {
        return accountRepository
                .findByAccountIdAndStatus(
                        identifier.getAccountId(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ApplicationException(
                                PaymentErrorCode.ACCOUNT_NOT_FOUND,
                                null,
                                Map.of("identifierValue", identifier.getIdentifierValue())
                        ));
    }

    private Wallet getWallet(String accountId, Party party, String currency, String role) {
        Wallet wallet = walletRepository
                .findByAccountIdAndCurrencyAndWalletType(
                        accountId,
                        currency,
                        party.getWalletType().name()
                )
                .orElseThrow(() ->
                        new ApplicationException(
                                PaymentErrorCode.WALLET_NOT_FOUND,
                                null,
                                Map.of(
                                        "role", role,
                                        "currency", currency,
                                        "walletType", party.getWalletType().name()
                                )
                        ));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_WALLET,
                    null,
                    Map.of("role", role)
            );
        }

        if (Boolean.TRUE.equals(wallet.getIsLocked())) {
            throw new ApplicationException(
                    PaymentErrorCode.WALLET_LOCKED,
                    null,
                    Map.of("role", role)
            );
        }

        return wallet;
    }

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }

    private void createTransactionRecord(
            String transactionId,
            BillPayPaymentRequest request,
            AccountIdentifier debitorIdentifier,
            AccountIdentifier creditorIdentifier,
            String debitorAccountType,
            String creditorAccountType,
            Wallet debitorWallet,
            Wallet creditorWallet
    ) {
        paymentTransactionRecorderService.recordTransaction(
                transactionId,
                request.getTransaction().getAmount(),
                request.getRequestGateway().name(),
                request.getOperationType(),
                request.getPreferredLang(),
                debitorIdentifier,
                creditorIdentifier,
                debitorAccountType,
                creditorAccountType,
                debitorWallet,
                creditorWallet,
                request.getInitiatedBy(),
                request.getMetadata(),
                request.getAdditionalInfo(),
                request.getPaymentReference(),
                request.getComments()
        );
    }
}
