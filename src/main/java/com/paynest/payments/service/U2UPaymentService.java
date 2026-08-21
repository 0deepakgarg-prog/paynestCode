package com.paynest.payments.service;


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
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.dto.U2UPaymentResponse;
import com.paynest.payments.validation.BasePaymentRequestValidator;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.service.PricingService;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.config.security.JWTUtils;
import com.paynest.users.service.AuthService;
import com.paynest.payments.service.BalanceService;
import com.paynest.payments.service.TransactionsService;
import com.paynest.config.tenant.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class U2UPaymentService {

    private static final Logger log = LoggerFactory.getLogger(U2UPaymentService.class);
    private static final String OPERATION_NAME = "U2U";
    private static final String TRANSACTION_PREFIX = "UU";
    private static final Set<AccountType> ALLOWED_ACCOUNT_TYPES = Set.of(
            AccountType.SUBSCRIBER,
            AccountType.AGENT,
            AccountType.MERCHANT,
            AccountType.BILLER,
            AccountType.BUSINESS
    );

    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final BasePaymentRequestValidator basePaymentRequestValidator;
    private final PropertyReader propertyReader;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;
    private final AuthService authService;
    private final PricingService pricingService;

    public U2UPaymentService(
            WalletRepository walletRepository,
            AccountRepository accountRepository,
            AccountIdentifierRepository accountIdentifierRepository,
            BasePaymentRequestValidator basePaymentRequestValidator,
            PropertyReader propertyReader,
            TransactionsService transactionsService,
            BalanceService balanceService,
            AuthService authService,
            PricingService pricingService
    ) {
        this.walletRepository = walletRepository;
        this.accountRepository = accountRepository;
        this.accountIdentifierRepository = accountIdentifierRepository;
        this.basePaymentRequestValidator = basePaymentRequestValidator;
        this.propertyReader = propertyReader;
        this.transactionsService = transactionsService;
        this.balanceService = balanceService;
        this.authService = authService;
        this.pricingService = pricingService;
    }

    public U2UPaymentResponse processPayment(U2UPaymentRequest request, boolean validateJWT) {
        log.info("Processing {} payment request. traceId={}", OPERATION_NAME, TraceContext.getTraceId());
        basePaymentRequestValidator.validate(request);
        normalizeRequest(request);
        String currency = request.getTransaction().getCurrency();

        validateParty(request.getDebitor(), InitiatedBy.DEBITOR);
        validateParty(request.getCreditor(), InitiatedBy.CREDITOR);
        validateMatchingWalletTypes(request.getDebitor(), request.getCreditor());

        AccountIdentifier debitorIdentifier = getIdentifier(request.getDebitor());
        AccountIdentifier creditorIdentifier = getIdentifier(request.getCreditor());
        validateDifferentAccounts(debitorIdentifier, creditorIdentifier);
        validateJwtAccess(
                validateJWT,
                debitorIdentifier,
                request.getDebitor().getAuthentication(),
                request.getDebitor().getAccountType()
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

        //TODO : SC calculation and applying the fees

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
        BigDecimal serviceChargeAmount = getServiceChargeAmount(pricingComputation);
        BigDecimal discountAmount = getDiscountAmount(pricingComputation);
        BigDecimal cashbackAmount = getCashbackAmount(pricingComputation);

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

            if (hasPricingAdjustments(pricingComputation)) {
                balanceService.transferWalletAmountWithPricing(
                        debitorWallet,
                        creditorWallet,
                        request.getTransaction().getAmount(),
                        request.getOperationType(),
                        request.getInitiatedBy(),
                        transactionId,
                        pricingComputation
                );
            } else {
                balanceService.transferWalletAmount(
                        debitorWallet,
                        creditorWallet,
                        request.getTransaction().getAmount(),
                        request.getOperationType(),
                        request.getInitiatedBy(),
                        transactionId
                );
            }
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(transactionId);
        }

        return buildSuccessResponse(
                request,
                transactionId,
                pricingComputation,
                serviceChargeAmount,
                discountAmount,
                cashbackAmount
        );
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

    private U2UPaymentResponse buildSuccessResponse(
            U2UPaymentRequest request,
            String transactionId,
            PricingComputationResponse pricingComputation,
            BigDecimal serviceChargeAmount,
            BigDecimal discountAmount,
            BigDecimal cashbackAmount
    ) {
        BigDecimal totalAmount = request.getTransaction().getAmount()
                .subtract(discountAmount)
                .add(serviceChargeAmount);
        return U2UPaymentResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(request.getOperationType())
                .code("PAYMENT_SUCCESS")
                .message("U2U Payment Successful")
                .timestamp(TenantTime.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .totalAmount(totalAmount)
                .currency(request.getTransaction().getCurrency())
                .serviceCharge(buildServiceChargeResponse(
                        request,
                        pricingComputation,
                        serviceChargeAmount
                ))
                .discount(buildDiscountResponse(
                        request,
                        pricingComputation,
                        discountAmount
                ))
                .cashback(buildCashbackResponse(
                        request,
                        pricingComputation,
                        cashbackAmount
                ))
                .build();
    }

    private U2UPaymentResponse.ServiceChargeDetails buildServiceChargeResponse(
            U2UPaymentRequest request,
            PricingComputationResponse pricingComputation,
            BigDecimal serviceChargeAmount
    ) {
        if (serviceChargeAmount.compareTo(BigDecimal.ZERO) <= 0 || pricingComputation == null) {
            return null;
        }

        return U2UPaymentResponse.ServiceChargeDetails.builder()
                .amount(serviceChargeAmount)
                .payer(pricingComputation.getServiceChargeAffectedParty())
                .currency(request.getTransaction().getCurrency())
                .build();
    }

    private U2UPaymentResponse.PricingAdjustmentDetails buildDiscountResponse(
            U2UPaymentRequest request,
            PricingComputationResponse pricingComputation,
            BigDecimal discountAmount
    ) {
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0 || pricingComputation == null) {
            return null;
        }

        return U2UPaymentResponse.PricingAdjustmentDetails.builder()
                .amount(discountAmount)
                .party(pricingComputation.getDiscountAffectedParty())
                .currency(request.getTransaction().getCurrency())
                .build();
    }

    private U2UPaymentResponse.PricingAdjustmentDetails buildCashbackResponse(
            U2UPaymentRequest request,
            PricingComputationResponse pricingComputation,
            BigDecimal cashbackAmount
    ) {
        if (cashbackAmount.compareTo(BigDecimal.ZERO) <= 0 || pricingComputation == null) {
            return null;
        }

        return U2UPaymentResponse.PricingAdjustmentDetails.builder()
                .amount(cashbackAmount)
                .party(pricingComputation.getCashbackAffectedParty())
                .payBy(pricingComputation.getCashbackPayBy())
                .currency(request.getTransaction().getCurrency())
                .build();
    }

    private void validateParty(Party party, InitiatedBy role) {
        if (party.getAccountType() == null || !ALLOWED_ACCOUNT_TYPES.contains(party.getAccountType())) {
            throw new ApplicationException(
                    role == InitiatedBy.DEBITOR
                            ? PaymentErrorCode.INVALID_DEBITOR_USER_TYPE
                            : PaymentErrorCode.INVALID_CREDITOR_USER_TYPE,
                    null,
                    Map.of(
                            "role", role.name(),
                            "accountType", String.valueOf(party.getAccountType()),
                            "allowedAccountTypes", ALLOWED_ACCOUNT_TYPES.toString(),
                            "operationType", OPERATION_NAME
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

    private void normalizeRequest(U2UPaymentRequest request) {
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
            U2UPaymentRequest request,
            AccountIdentifier debitorIdentifier,
            AccountIdentifier creditorIdentifier,
            String debitorAccountType,
            String creditorAccountType,
            Wallet debitorWallet,
            Wallet creditorWallet
    ) {
        transactionsService.generateTransactionRecord(
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
                request.getPaymentReference(),
                request.getComments(),
                isPaymentViaQr(request.getMetadata()),
                request.getMetadata(),
                request.getAdditionalInfo()
        );

        transactionsService.updateOptionalTransactionFields(
                transactionId,
                request.getMetadata(),
                request.getAdditionalInfo(),
                request.getPaymentReference(),
                request.getComments()
        );
    }

    private boolean isPaymentViaQr(Map<String, Object> metadata) {
        if (metadata == null) {
            return false;
        }

        Object paymentViaQr = metadata.get("paymentViaQr");
        return Boolean.TRUE.equals(paymentViaQr) || "true".equalsIgnoreCase(String.valueOf(paymentViaQr));
    }
}
