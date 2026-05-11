package com.paynest.payments.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.config.security.JWTUtils;
import com.paynest.config.tenant.TenantTime;
import com.paynest.config.tenant.TraceContext;
import com.paynest.enums.AccountType;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.fx.entity.FxRate;
import com.paynest.fx.repository.FxRateRepository;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.IntraWalletTransferRequest;
import com.paynest.payments.dto.IntraWalletTransferResponse;
import com.paynest.payments.dto.Party;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.AuthService;
import com.paynest.users.service.WalletCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IntraWalletTransferService {

    private static final String OPERATION_NAME = "INTRAWALLET";
    private static final String TRANSACTION_PREFIX = "IW";
    private static final String PIVOT_CURRENCY = "USD";
    private static final String MAIN_WALLET_TYPE = "MAIN";
    private static final String OPERATOR_ACCOUNT_ID_PROPERTY_PREFIX = "operator.account-id.";
    private static final String DEFAULT_OPERATOR_ACCOUNT_ID_PROPERTY = "operator.account-id.default";
    private static final String SYSTEM_CONFIG_ENUM_TYPE = "SYSTEM_CONFIG";
    private static final String BONUS_TO_MAIN_PERCENTAGE_CONFIG = "INTRAWALLET_BONUS_TO_MAIN_PERCENTAGE";
    private static final Set<AccountType> ALLOWED_ACCOUNT_TYPES = Set.of(
            AccountType.SUBSCRIBER,
            AccountType.AGENT,
            AccountType.MERCHANT,
            AccountType.BILLER
    );

    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final FxRateRepository fxRateRepository;
    private final EnumerationRepository enumerationRepository;
    private final PropertyReader propertyReader;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;
    private final AuthService authService;
    private final WalletCacheService walletCacheService;

    @Transactional
    public IntraWalletTransferResponse processTransfer(IntraWalletTransferRequest request) {
        validateRequest(request);
        Party party = request.getParty();
        validateParty(party);
        normalizeRequest(request);
        WalletType sourceWalletType = resolveSourceWalletType(request);
        WalletType targetWalletType = resolveTargetWalletType(request);
        validateWalletTransferTarget(sourceWalletType, targetWalletType, request);

        AccountIdentifier accountIdentifier = getIdentifier(party);
        validateJwtAccess(accountIdentifier, party.getAuthentication(), party.getAccountType());

        Account account = getAccount(accountIdentifier);
        validateAccountType(account, party.getAccountType());

        Authentication authentication = party.getAuthentication();
        authService.validateAuthentication(
                authentication.getValue(),
                authentication.getType(),
                accountIdentifier
        );

        Wallet debitorWallet = getWallet(
                account.getAccountId(),
                sourceWalletType,
                request.getSourceCurrency(),
                InitiatedBy.DEBITOR.name()
        );
        Wallet creditorWallet = getWallet(
                account.getAccountId(),
                targetWalletType,
                request.getTargetCurrency(),
                InitiatedBy.CREDITOR.name()
        );

        BigDecimal fxConvertedAmount = convertViaUsd(
                request.getAmount(),
                request.getSourceCurrency(),
                request.getTargetCurrency()
        );
        BigDecimal bonusToMainPercentage = resolveBonusToMainPercentage(sourceWalletType, targetWalletType);
        BigDecimal targetAmount = applyWalletTypeConversion(fxConvertedAmount, bonusToMainPercentage);
        BigDecimal exchangeRate = targetAmount.divide(request.getAmount(), 10, RoundingMode.HALF_UP);

        String transactionId = IdGenerator.generateTransactionId(
                TRANSACTION_PREFIX,
                getRequiredServerInstance()
        );
        boolean crossCurrencyTransfer = !request.getSourceCurrency().equals(request.getTargetCurrency());

        try {
            Wallet systemSourceWallet = null;
            Wallet systemTargetWallet = null;
            if (crossCurrencyTransfer) {
                systemSourceWallet = getSystemMainWallet(request.getSourceCurrency(), InitiatedBy.CREDITOR.name());
                systemTargetWallet = getSystemMainWallet(request.getTargetCurrency(), InitiatedBy.DEBITOR.name());

                transactionsService.generateCurrencyExchangeTransactionRecord(
                        transactionId,
                        request.getAmount(),
                        targetAmount,
                        request.getRequestGateway().name(),
                        OPERATION_NAME,
                        request.getPreferredLang(),
                        accountIdentifier,
                        account.getAccountType(),
                        debitorWallet,
                        systemSourceWallet,
                        systemTargetWallet,
                        creditorWallet,
                        InitiatedBy.DEBITOR,
                        exchangeRate,
                        bonusToMainPercentage
                );
            } else {
                transactionsService.generateTransactionRecord(
                        transactionId,
                        request.getAmount(),
                        targetAmount,
                        request.getRequestGateway().name(),
                        OPERATION_NAME,
                        request.getPreferredLang(),
                        accountIdentifier,
                        accountIdentifier,
                        account.getAccountType(),
                        account.getAccountType(),
                        debitorWallet,
                        creditorWallet,
                        InitiatedBy.DEBITOR
                );
            }

            transactionsService.updateOptionalTransactionFields(
                    transactionId,
                    request.getMetadata(),
                    request.getAdditionalInfo(),
                    request.getPaymentReference(),
                    request.getComments()
            );

            if (request.getSourceCurrency().equals(request.getTargetCurrency())
                    && request.getAmount().compareTo(targetAmount) == 0) {
                balanceService.transferWalletAmount(
                        debitorWallet,
                        creditorWallet,
                        request.getAmount(),
                        OPERATION_NAME,
                        InitiatedBy.DEBITOR,
                        transactionId
                );
            } else if (request.getSourceCurrency().equals(request.getTargetCurrency())) {
                balanceService.transferCrossCurrencyWalletAmount(
                        debitorWallet,
                        creditorWallet,
                        request.getAmount(),
                        targetAmount,
                        OPERATION_NAME,
                        InitiatedBy.DEBITOR,
                        transactionId
                );
            } else {
                balanceService.transferCurrencyExchangeWalletAmount(
                        debitorWallet,
                        systemSourceWallet,
                        systemTargetWallet,
                        creditorWallet,
                        request.getAmount(),
                        targetAmount,
                        OPERATION_NAME,
                        InitiatedBy.DEBITOR,
                        transactionId
                );
            }
            refreshWalletCacheAfterCommit(debitorWallet, creditorWallet, systemSourceWallet, systemTargetWallet);
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(transactionId);
        }

        return IntraWalletTransferResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(OPERATION_NAME)
                .code("PAYMENT_SUCCESS")
                .message("Intra-wallet transfer successful")
                .timestamp(TenantTime.instant())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .sourceAmount(request.getAmount())
                .sourceWalletType(sourceWalletType.name())
                .sourceCurrency(request.getSourceCurrency())
                .targetAmount(targetAmount)
                .targetWalletType(targetWalletType.name())
                .targetCurrency(request.getTargetCurrency())
                .exchangeRate(exchangeRate)
                .bonusToMainPercentage(bonusToMainPercentage)
                .build();
    }

    private void validateRequest(IntraWalletTransferRequest request) {
        if (request == null) {
            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }
        if (request.getRequestGateway() == null) {
            throw new ApplicationException(PaymentErrorCode.REQUEST_GATEWAY_MISSING);
        }
        if (request.getParty() == null) {
            throw new ApplicationException(PaymentErrorCode.DEBTOR_MISSING);
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT);
        }
        if (request.getAmount().scale() > 2) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT_SCALE);
        }
        if (request.getSourceCurrency() == null || request.getSourceCurrency().isBlank()
                || request.getTargetCurrency() == null || request.getTargetCurrency().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.CURRENCY_MISSING);
        }
    }

    private void normalizeRequest(IntraWalletTransferRequest request) {
        request.setSourceCurrency(normalizeCurrency(request.getSourceCurrency()));
        request.setTargetCurrency(normalizeCurrency(request.getTargetCurrency()));
        request.setPreferredLang(normalizeOptionalText(request.getPreferredLang(), "en"));
        request.setPaymentReference(normalizeOptionalText(request.getPaymentReference(), null));
        request.setComments(normalizeOptionalText(request.getComments(), null));
        request.getParty().getIdentifier().setValue(request.getParty().getIdentifier().getValue().trim());
    }

    private WalletType resolveSourceWalletType(IntraWalletTransferRequest request) {
        return request.getSourceWalletType() == null
                ? request.getParty().getWalletType()
                : request.getSourceWalletType();
    }

    private WalletType resolveTargetWalletType(IntraWalletTransferRequest request) {
        return request.getTargetWalletType() == null
                ? request.getParty().getWalletType()
                : request.getTargetWalletType();
    }

    private void validateWalletTransferTarget(
            WalletType sourceWalletType,
            WalletType targetWalletType,
            IntraWalletTransferRequest request
    ) {
        if (sourceWalletType == null || targetWalletType == null) {
            throw new ApplicationException(PaymentErrorCode.WALLET_TYPE_MISSING);
        }
        if (sourceWalletType == targetWalletType
                && request.getSourceCurrency().equals(request.getTargetCurrency())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_WALLET,
                    null,
                    Map.of(
                            "sourceWalletType", sourceWalletType.name(),
                            "targetWalletType", targetWalletType.name(),
                            "sourceCurrency", request.getSourceCurrency(),
                            "targetCurrency", request.getTargetCurrency()
                    )
            );
        }
    }

    private BigDecimal resolveBonusToMainPercentage(WalletType sourceWalletType, WalletType targetWalletType) {
        if (sourceWalletType == WalletType.BONUS && targetWalletType == WalletType.MAIN) {
            String configuredPercentage = enumerationRepository
                    .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
                            SYSTEM_CONFIG_ENUM_TYPE,
                            BONUS_TO_MAIN_PERCENTAGE_CONFIG
                    )
                    .orElseThrow(() -> new IllegalStateException(BONUS_TO_MAIN_PERCENTAGE_CONFIG + " is not configured"))
                    .getEnumValue();
            BigDecimal percentage = new BigDecimal(configuredPercentage.trim());
            if (percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalStateException(BONUS_TO_MAIN_PERCENTAGE_CONFIG + " must be between 0 and 100");
            }
            return percentage;
        }
        return new BigDecimal("100");
    }

    private BigDecimal applyWalletTypeConversion(BigDecimal fxConvertedAmount, BigDecimal bonusToMainPercentage) {
        return fxConvertedAmount
                .multiply(bonusToMainPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CURRENCY,
                    null,
                    Map.of("currency", currency)
            );
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private void validateParty(Party party) {
        if (party.getAccountType() == null || !ALLOWED_ACCOUNT_TYPES.contains(party.getAccountType())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_DEBITOR_USER_TYPE,
                    null,
                    Map.of(
                            "accountType", String.valueOf(party.getAccountType()),
                            "allowedAccountTypes", ALLOWED_ACCOUNT_TYPES.toString(),
                            "operationType", OPERATION_NAME
                    )
            );
        }
        validateIdentifier(party.getIdentifier());
        validateAuthentication(party.getAuthentication());
    }

    private void validateIdentifier(Identifier identifier) {
        if (identifier == null) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_MISSING);
        }
        if (identifier.getType() == null) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_TYPE_MISSING);
        }
        if (identifier.getValue() == null || identifier.getValue().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_VALUE_MISSING);
        }
    }

    private void validateAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new ApplicationException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (authentication.getType() == null) {
            throw new ApplicationException(PaymentErrorCode.AUTH_TYPE_MISSING);
        }
        if (authentication.getValue() == null || authentication.getValue().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.AUTH_VALUE_MISSING);
        }
    }

    private void validateJwtAccess(
            AccountIdentifier accountIdentifier,
            Authentication requestedAuthentication,
            AccountType expectedAccountType
    ) {
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

        if (!currentAccountId.equalsIgnoreCase(accountIdentifier.getAccountId())) {
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
                    Map.of("operationType", OPERATION_NAME)
            );
        }
        if (!requestedAuthentication.getType().name().equalsIgnoreCase(currentAuthType)) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_AUTH_TYPE,
                    null,
                    Map.of("operationType", OPERATION_NAME)
            );
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

    private void validateAccountType(Account account, AccountType expectedType) {
        if (!account.getAccountType().equalsIgnoreCase(expectedType.name())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_DEBITOR_ACCOUNT_TYPE,
                    null,
                    Map.of(
                            "expectedType", expectedType.name(),
                            "actualType", account.getAccountType()
                    )
            );
        }
    }

    private Wallet getWallet(String accountId, WalletType walletType, String currency, String role) {
        Wallet wallet = walletRepository
                .findByAccountIdAndCurrencyAndWalletType(
                        accountId,
                        currency,
                        walletType.name()
                )
                .orElseThrow(() ->
                        new ApplicationException(
                                PaymentErrorCode.WALLET_NOT_FOUND,
                                null,
                                Map.of(
                                        "role", role,
                                        "currency", currency,
                                        "walletType", walletType.name()
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

    private Wallet getSystemMainWallet(String currency, String role) {
        String operatorAccountId = resolveOperatorAccountId(currency);
        Wallet wallet = walletRepository
                .findByAccountIdAndCurrencyAndWalletType(
                        operatorAccountId,
                        currency,
                        MAIN_WALLET_TYPE
                )
                .orElseThrow(() ->
                        new ApplicationException(
                                PaymentErrorCode.WALLET_NOT_FOUND,
                                null,
                                Map.of(
                                        "role", role,
                                        "currency", currency,
                                        "walletType", MAIN_WALLET_TYPE,
                                        "accountId", operatorAccountId
                                )
                        ));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_WALLET,
                    null,
                    Map.of("role", role, "accountId", operatorAccountId)
            );
        }
        if (Boolean.TRUE.equals(wallet.getIsLocked())) {
            throw new ApplicationException(
                    PaymentErrorCode.WALLET_LOCKED,
                    null,
                    Map.of("role", role, "accountId", operatorAccountId)
            );
        }
        return wallet;
    }

    private String resolveOperatorAccountId(String currency) {
        String currencySpecificAccountId = propertyReader.getPropertyValue(OPERATOR_ACCOUNT_ID_PROPERTY_PREFIX + currency);
        if (currencySpecificAccountId != null && !currencySpecificAccountId.isBlank()) {
            return currencySpecificAccountId.trim();
        }

        String defaultAccountId = propertyReader.getPropertyValue(DEFAULT_OPERATOR_ACCOUNT_ID_PROPERTY);
        if (defaultAccountId == null || defaultAccountId.isBlank()) {
            throw new IllegalStateException(DEFAULT_OPERATOR_ACCOUNT_ID_PROPERTY + " is not configured");
        }
        return defaultAccountId.trim();
    }

    private BigDecimal convertViaUsd(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        BigDecimal sourceUsdRate = getUsdRate(sourceCurrency);
        BigDecimal targetUsdRate = getUsdRate(targetCurrency);

        return amount
                .divide(sourceUsdRate, 10, RoundingMode.HALF_UP)
                .multiply(targetUsdRate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getUsdRate(String currency) {
        if (PIVOT_CURRENCY.equals(currency)) {
            return BigDecimal.ONE;
        }

        LocalDateTime now = TenantTime.now();
        return fxRateRepository
                .findFirstByTargetCurrencyAndIsActiveTrueAndValidFromLessThanEqualOrderByVersionNoDesc(currency, now)
                .map(FxRate::getUsdRate)
                .orElseThrow(() ->
                        new ApplicationException(
                                PaymentErrorCode.INVALID_CURRENCY,
                                null,
                                Map.of("currency", currency)
                        ));
    }

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }

    private void refreshWalletCacheAfterCommit(Wallet... wallets) {
        Set<String> accountIds = new LinkedHashSet<>();
        for (Wallet wallet : wallets) {
            if (wallet != null && wallet.getAccountId() != null && !wallet.getAccountId().isBlank()) {
                accountIds.add(wallet.getAccountId());
            }
        }
        if (accountIds.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accountIds.forEach(walletCacheService::refreshAccountWallets);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accountIds.forEach(walletCacheService::refreshAccountWallets);
            }
        });
    }
}
