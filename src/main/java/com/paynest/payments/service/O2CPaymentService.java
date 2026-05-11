package com.paynest.payments.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.entity.SupportedLanguage;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.config.repository.SupportedLanguageRepository;
import com.paynest.config.tenant.TenantTime;
import com.paynest.config.tenant.TraceContext;
import com.paynest.enums.AccountType;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.CommonErrorCode;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.O2CApprovalRequest;
import com.paynest.payments.dto.O2CPaymentRequest;
import com.paynest.payments.dto.Party;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.tenant.RequestLanguageContext;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.paynest.config.security.JWTUtils.getCurrentAccountType;
import static com.paynest.config.security.JWTUtils.getCurrentAccountId;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class O2CPaymentService {

    private static final String OPERATION_NAME = "O2C";
    private static final String TRANSACTION_PREFIX = "OC";
    private static final String DEFAULT_OPERATOR_ACCOUNT_ID = "SYS0001";
    private static final String OPERATOR_ACCOUNT_PROPERTY_PREFIX = "operator.account-id.";
    private static final String OPERATOR_DEFAULT_ACCOUNT_PROPERTY = "operator.account-id.default";
    private static final String OPERATOR_WALLET_TYPE = "MAIN";
    private static final Set<AccountType> CHANNEL_ACCOUNT_TYPES = Set.of(
            AccountType.AGENT,
            AccountType.MERCHANT,
            AccountType.BILLER
    );

    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final WalletRepository walletRepository;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final EnumerationRepository enumerationRepository;
    private final SupportedLanguageRepository supportedLanguageRepository;
    private final PropertyReader propertyReader;
    private final com.paynest.payments.service.TransactionsService transactionsService;
    private final BalanceService balanceService;

    public BasePaymentResponse processPayment(O2CPaymentRequest request) {
        log.info("O2C initiate started. traceId={}, currentAccountId={}, currentAccountType={}",
                TraceContext.getTraceId(), getCurrentAccountId(), getCurrentAccountType());
        validateAdminAccess();
        log.debug("O2C initiate admin access validated. traceId={}", TraceContext.getTraceId());
        validateRequest(request);
        log.debug("O2C initiate request validated. traceId={}, requestGateway={}, amount={}, currency={}, channelType={}, channelIdentifierType={}",
                TraceContext.getTraceId(),
                request.getRequestGateway(),
                request.getTransaction().getAmount(),
                request.getTransaction().getCurrency(),
                request.getChannel().getAccountType(),
                request.getChannel().getIdentifier().getType());
        normalizeRequest(request);
        log.debug("O2C initiate request normalized. traceId={}, currency={}, preferredLang={}, paymentReferencePresent={}, commentsPresent={}",
                TraceContext.getTraceId(),
                request.getTransaction().getCurrency(),
                request.getPreferredLang(),
                request.getPaymentReference() != null,
                request.getComments() != null);

        String operatorAccountId = resolveOperatorAccountId(request.getTransaction().getCurrency());
        log.debug("O2C initiate resolved operator account. traceId={}, operatorAccountId={}, currency={}",
                TraceContext.getTraceId(), operatorAccountId, request.getTransaction().getCurrency());
        Account operatorAccount = getActiveAccount(operatorAccountId);
        AccountIdentifier operatorIdentifier = buildAccountIdentifier(operatorAccount.getAccountId());
        Wallet operatorWallet = getWallet(
                operatorAccount.getAccountId(),
                request.getTransaction().getCurrency(),
                OPERATOR_WALLET_TYPE,
                "OPERATOR"
        );
        log.debug("O2C initiate resolved operator wallet. traceId={}, operatorAccountId={}, operatorWalletId={}, currency={}, walletType={}",
                TraceContext.getTraceId(), operatorAccount.getAccountId(), operatorWallet.getWalletId(),
                operatorWallet.getCurrency(), operatorWallet.getWalletType());

        AccountIdentifier channelIdentifier = getIdentifier(request.getChannel());
        log.debug("O2C initiate resolved channel identifier. traceId={}, channelAccountId={}, identifierType={}",
                TraceContext.getTraceId(), channelIdentifier.getAccountId(), channelIdentifier.getIdentifierType());
        Account channelAccount = getActiveAccount(channelIdentifier.getAccountId());
        validateChannelAccount(channelAccount, request.getChannel().getAccountType());
        Wallet channelWallet = getWallet(
                channelAccount.getAccountId(),
                request.getTransaction().getCurrency(),
                request.getChannel().getWalletType().name(),
                "CHANNEL"
        );
        log.debug("O2C initiate resolved channel wallet. traceId={}, channelAccountId={}, channelWalletId={}, currency={}, walletType={}",
                TraceContext.getTraceId(), channelAccount.getAccountId(), channelWallet.getWalletId(),
                channelWallet.getCurrency(), channelWallet.getWalletType());

        String transactionId = IdGenerator.generateTransactionId(
                TRANSACTION_PREFIX,
                getRequiredServerInstance()
        );
        log.info("O2C initiate generated transaction id. traceId={}, transactionId={}",
                TraceContext.getTraceId(), transactionId);

        try {
            log.debug("O2C initiate creating transaction record. traceId={}, transactionId={}, amount={}, operatorWalletId={}, channelWalletId={}",
                    TraceContext.getTraceId(), transactionId, request.getTransaction().getAmount(),
                    operatorWallet.getWalletId(), channelWallet.getWalletId());
            transactionsService.generateTransactionRecord(
                    transactionId,
                    request.getTransaction().getAmount(),
                    request.getRequestGateway().name(),
                    OPERATION_NAME,
                    request.getPreferredLang(),
                    operatorIdentifier,
                    channelIdentifier,
                    operatorAccount.getAccountType(),
                    channelAccount.getAccountType(),
                    operatorWallet,
                    channelWallet,
                    InitiatedBy.DEBITOR
            );
            log.debug("O2C initiate transaction record created. traceId={}, transactionId={}",
                    TraceContext.getTraceId(), transactionId);
            //updateOptionalTransactionFields(transactionId, request);
            log.debug("O2C initiate optional transaction fields updated. traceId={}, transactionId={}",
                    TraceContext.getTraceId(), transactionId);
            recordTransactionInitiator(transactionId, getCurrentAccountId());
            log.debug("O2C initiate transaction initiator recorded. traceId={}, transactionId={}, initiatorAccountId={}",
                    TraceContext.getTraceId(), transactionId, getCurrentAccountId());
        } catch (ApplicationException ex) {
            log.warn("O2C initiate failed with application exception. traceId={}, transactionId={}, errorCode={}, message={}",
                    TraceContext.getTraceId(), transactionId, ex.getErrorCode(), ex.getMessage());
            throw ex.withTransactionId(transactionId);
        }

        log.info("O2C initiate completed. traceId={}, transactionId={}, channelAccountId={}, amount={}, currency={}",
                TraceContext.getTraceId(), transactionId, channelAccount.getAccountId(),
                request.getTransaction().getAmount(), request.getTransaction().getCurrency());
        return BasePaymentResponse.builder()
                .responseStatus(TransactionStatus.PENDING)
                .operationType(OPERATION_NAME)
                .code("O2C_INITIATED")
                .message("O2C transaction initiated and pending approval")
                .timestamp(TenantTime.instant())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .currency(request.getTransaction().getCurrency())
                .build();
    }

    public BasePaymentResponse updateO2CTransactionStatus(O2CApprovalRequest request) {
        log.info("O2C status update started. traceId={}, transactionId={}, requestedStatus={}, currentAccountId={}, currentAccountType={}",
                TraceContext.getTraceId(),
                request != null ? request.getTransactionId() : null,
                request != null ? request.getStatus() : null,
                getCurrentAccountId(),
                getCurrentAccountType());
        validateAdminAccess();
        log.debug("O2C status update admin access validated. traceId={}, transactionId={}",
                TraceContext.getTraceId(), request != null ? request.getTransactionId() : null);
        validateApprovalRequest(request);
        log.debug("O2C status update request validated. traceId={}, transactionId={}, requestedStatus={}",
                TraceContext.getTraceId(), request.getTransactionId(), request.getStatus());

        String currentAccountId = getCurrentAccountId();
        log.debug("O2C status update loading transaction. traceId={}, transactionId={}",
                TraceContext.getTraceId(), request.getTransactionId());
        Transactions transaction = transactionsRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TXN_NOT_FOUND, "Transaction not found"));
        log.debug("O2C status update loaded transaction. traceId={}, transactionId={}, serviceCode={}, status={}, createdBy={}, modifiedBy={}, amount={}",
                TraceContext.getTraceId(), transaction.getTransactionId(), transaction.getServiceCode(),
                transaction.getTransferStatus(), transaction.getCreatedBy(), transaction.getModifiedBy(),
                transaction.getTransactionValue());

        if (!OPERATION_NAME.equalsIgnoreCase(transaction.getServiceCode())) {
            log.warn("O2C status update rejected due to invalid transaction type. traceId={}, transactionId={}, serviceCode={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), transaction.getServiceCode());
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_TYPE,
                    "Transaction is not an O2C transaction"
            );
        }
        if (!Constants.TRANSACTION_INITIATED.equalsIgnoreCase(transaction.getTransferStatus())
                && !Constants.TRANSACTION_PENDING.equalsIgnoreCase(transaction.getTransferStatus())) {
            log.warn("O2C status update rejected due to invalid status. traceId={}, transactionId={}, currentStatus={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), transaction.getTransferStatus());
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_STATUS,
                    "Only initiated or pending O2C transactions can be updated"
            );
        }
        if (currentAccountId.equalsIgnoreCase(transaction.getCreatedBy())) {
            log.warn("O2C status update rejected because initiator attempted approval. traceId={}, transactionId={}, accountId={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), currentAccountId);
            throw new ApplicationException(
                    ErrorCodes.INVALID_INITIATOR,
                    "The user who initiated the O2C transaction cannot approve or reject it"
            );
        }

        log.debug("O2C status update loading transaction details. traceId={}, transactionId={}",
                TraceContext.getTraceId(), request.getTransactionId());
        List<TransactionDetails> transactionDetails = transactionDetailsRepository
                .findByIdTransactionId(request.getTransactionId());
        log.debug("O2C status update loaded transaction details. traceId={}, transactionId={}, detailCount={}",
                TraceContext.getTraceId(), request.getTransactionId(), transactionDetails.size());
        if (transactionDetails.size() != 2) {
            log.warn("O2C status update rejected due to invalid detail count. traceId={}, transactionId={}, detailCount={}",
                    TraceContext.getTraceId(), request.getTransactionId(), transactionDetails.size());
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_DETAILS,
                    "Expected exactly two transaction details for O2C transaction"
            );
        }

        TransactionDetails debitDetail = getTransactionDetail(transactionDetails, 1L);
        TransactionDetails creditDetail = getTransactionDetail(transactionDetails, 2L);
        Wallet operatorWallet = getWalletById(debitDetail.getWalletNumber(), "OPERATOR");
        Wallet channelWallet = getWalletById(creditDetail.getWalletNumber(), "CHANNEL");
        log.debug("O2C status update resolved wallets. traceId={}, transactionId={}, operatorWalletId={}, channelWalletId={}, currency={}",
                TraceContext.getTraceId(), transaction.getTransactionId(), operatorWallet.getWalletId(),
                channelWallet.getWalletId(), channelWallet.getCurrency());

        if ("APPROVED".equalsIgnoreCase(request.getStatus())) {
            BigDecimal amount = toDisplayAmount(transaction.getTransactionValue());
            log.info("O2C approval balance transfer starting. traceId={}, transactionId={}, amount={}, operatorWalletId={}, channelWalletId={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), amount,
                    operatorWallet.getWalletId(), channelWallet.getWalletId());
            balanceService.transferWalletAmount(
                    operatorWallet,
                    channelWallet,
                    amount,
                    OPERATION_NAME,
                    InitiatedBy.DEBITOR,
                    transaction.getTransactionId()
            );
            log.info("O2C approval balance transfer completed. traceId={}, transactionId={}",
                    TraceContext.getTraceId(), transaction.getTransactionId());
            updateApproveOrRejectComments(transaction.getTransactionId(), request.getComments());
            log.debug("O2C approval comments updated. traceId={}, transactionId={}, commentsPresent={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(),
                    request.getComments() != null && !request.getComments().isBlank());
            recordTransactionModifier(transaction.getTransactionId(), currentAccountId);
            log.debug("O2C approval modifier recorded. traceId={}, transactionId={}, modifierAccountId={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), currentAccountId);

            log.info("O2C approval completed. traceId={}, transactionId={}, channelAccountId={}, amount={}, currency={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), channelWallet.getAccountId(),
                    amount, channelWallet.getCurrency());
            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.SUCCESS)
                    .operationType(OPERATION_NAME)
                    .code("O2C_APPROVED")
                    .message("O2C transaction approved successfully")
                    .timestamp(TenantTime.instant())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transaction.getTransactionId())
                    .amount(amount)
                    .currency(channelWallet.getCurrency())
                    .build();
        }

        if ("REJECTED".equalsIgnoreCase(request.getStatus())) {
            String errorCode = "O2C_REJECTED";
            log.info("O2C rejection started. traceId={}, transactionId={}, errorCode={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), errorCode);

            updateApproveOrRejectComments(transaction.getTransactionId(), request.getComments());
            log.debug("O2C rejection comments updated. traceId={}, transactionId={}, commentsPresent={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(),
                    request.getComments() != null && !request.getComments().isBlank());
            transactionsService.updateFailedTransactionRecord(
                    transaction.getTransactionId(),
                    errorCode,
                    currentAccountId
            );
            log.info("O2C rejection completed. traceId={}, transactionId={}, modifierAccountId={}",
                    TraceContext.getTraceId(), transaction.getTransactionId(), currentAccountId);

            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.FAILURE)
                    .operationType(OPERATION_NAME)
                    .code(errorCode)
                    .message("O2C transaction rejected")
                    .timestamp(TenantTime.instant())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transaction.getTransactionId())
                    .amount(toDisplayAmount(transaction.getTransactionValue()))
                    .currency(channelWallet.getCurrency())
                    .build();
        }

        log.warn("O2C status update rejected due to unsupported requested status. traceId={}, transactionId={}, requestedStatus={}",
                TraceContext.getTraceId(), transaction.getTransactionId(), request.getStatus());
        throw new ApplicationException(
                ErrorCodes.INVALID_STATUS,
                "Supported status values are APPROVED or REJECTED"
        );
    }

    private void validateAdminAccess() {
        log.debug("O2C validating admin access. traceId={}, currentAccountId={}, currentAccountType={}",
                TraceContext.getTraceId(), getCurrentAccountId(), getCurrentAccountType());
        if (!"ADMIN".equalsIgnoreCase(getCurrentAccountType())) {
            log.warn("O2C admin access validation failed. traceId={}, currentAccountId={}, currentAccountType={}",
                    TraceContext.getTraceId(), getCurrentAccountId(), getCurrentAccountType());
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }
    }

    private void validateRequest(O2CPaymentRequest request) {
        log.debug("O2C validating initiate request. traceId={}, requestNull={}",
                TraceContext.getTraceId(), request == null);
        if (request == null) {
            log.warn("O2C initiate request validation failed: request body is null. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }
        if (request.getRequestGateway() == null) {
            log.warn("O2C initiate request validation failed: request gateway missing. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(PaymentErrorCode.REQUEST_GATEWAY_MISSING);
        }
        validateChannel(request.getChannel());
        validateTransaction(request);
        log.debug("O2C initiate request validation completed. traceId={}", TraceContext.getTraceId());
    }

    private void validateChannel(Party channel) {
        log.debug("O2C validating channel. traceId={}, channelNull={}", TraceContext.getTraceId(), channel == null);
        if (channel == null) {
            log.warn("O2C channel validation failed: channel missing. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(PaymentErrorCode.CREDITOR_MISSING);
        }
        if (channel.getAccountType() == null || !CHANNEL_ACCOUNT_TYPES.contains(channel.getAccountType())) {
            log.warn("O2C channel validation failed: invalid channel account type. traceId={}, accountType={}",
                    TraceContext.getTraceId(), channel.getAccountType());
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CREDITOR_USER_TYPE,
                    null,
                    Map.of(
                            "role", "CHANNEL",
                            "accountType", String.valueOf(channel.getAccountType()),
                            "operationType", OPERATION_NAME
                    )
            );
        }
        if (channel.getIdentifier() == null) {
            log.warn("O2C channel validation failed: identifier missing. traceId={}, accountType={}",
                    TraceContext.getTraceId(), channel.getAccountType());
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_MISSING);
        }
        if (channel.getIdentifier().getType() == null) {
            log.warn("O2C channel validation failed: identifier type missing. traceId={}, accountType={}",
                    TraceContext.getTraceId(), channel.getAccountType());
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_TYPE_MISSING);
        }
        if (channel.getIdentifier().getValue() == null || channel.getIdentifier().getValue().isBlank()) {
            log.warn("O2C channel validation failed: identifier value missing. traceId={}, accountType={}, identifierType={}",
                    TraceContext.getTraceId(), channel.getAccountType(), channel.getIdentifier().getType());
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_VALUE_MISSING);
        }
        if (channel.getWalletType() == null) {
            log.warn("O2C channel validation failed: wallet type missing. traceId={}, accountType={}, identifierType={}",
                    TraceContext.getTraceId(), channel.getAccountType(), channel.getIdentifier().getType());
            throw new ApplicationException(PaymentErrorCode.WALLET_TYPE_MISSING);
        }
        log.debug("O2C channel validation completed. traceId={}, accountType={}, identifierType={}, walletType={}",
                TraceContext.getTraceId(), channel.getAccountType(), channel.getIdentifier().getType(), channel.getWalletType());
    }

    private void validateTransaction(O2CPaymentRequest request) {
        log.debug("O2C validating transaction. traceId={}, transactionNull={}",
                TraceContext.getTraceId(), request.getTransaction() == null);
        if (request.getTransaction() == null) {
            log.warn("O2C transaction validation failed: transaction missing. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }
        BigDecimal amount = request.getTransaction().getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > 2) {
            log.warn("O2C transaction validation failed: invalid amount. traceId={}, amount={}",
                    TraceContext.getTraceId(), amount);
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT);
        }
        if (request.getTransaction().getCurrency() == null || request.getTransaction().getCurrency().isBlank()) {
            log.warn("O2C transaction validation failed: currency missing. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(PaymentErrorCode.CURRENCY_MISSING);
        }
        String normalizedCurrency = request.getTransaction().getCurrency().trim().toUpperCase(Locale.ROOT);
        log.debug("O2C validating currency enumeration. traceId={}, currency={}",
                TraceContext.getTraceId(), normalizedCurrency);
        if (!enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
                "CURRENCY",
                normalizedCurrency
        )) {
            log.warn("O2C transaction validation failed: invalid currency. traceId={}, currency={}",
                    TraceContext.getTraceId(), request.getTransaction().getCurrency());
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CURRENCY,
                    null,
                    Map.of("currency", request.getTransaction().getCurrency())
            );
        }
        log.debug("O2C transaction validation completed. traceId={}, amount={}, currency={}",
                TraceContext.getTraceId(), amount, normalizedCurrency);
    }

    private void normalizeRequest(O2CPaymentRequest request) {
        log.debug("O2C normalizing request. traceId={}", TraceContext.getTraceId());
        request.setOperationType(OPERATION_NAME);
        request.getTransaction().setCurrency(
                request.getTransaction().getCurrency().trim().toUpperCase(Locale.ROOT)
        );
        request.setPreferredLang(resolvePreferredLanguage(request.getPreferredLang()));
        request.setPaymentReference(normalizeOptionalText(request.getPaymentReference()));
        request.setComments(normalizeOptionalText(request.getComments()));
        request.getChannel().getIdentifier().setValue(request.getChannel().getIdentifier().getValue().trim());
        log.debug("O2C request normalized. traceId={}, currency={}, preferredLang={}",
                TraceContext.getTraceId(), request.getTransaction().getCurrency(), request.getPreferredLang());
    }

    private String resolvePreferredLanguage(String preferredLang) {
        String normalizedLanguage = preferredLang == null ? null : preferredLang.trim().toLowerCase(Locale.ROOT);
        log.debug("O2C resolving preferred language. traceId={}, requestedLanguage={}",
                TraceContext.getTraceId(), normalizedLanguage);
        SupportedLanguage language = normalizedLanguage == null || normalizedLanguage.isBlank()
                ? getDefaultActiveLanguage()
                : supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue(normalizedLanguage)
                  .orElseGet(this::getDefaultActiveLanguage);

        String languageCode = language.getLanguageCode().trim().toLowerCase(Locale.ROOT);
        RequestLanguageContext.setLanguage(languageCode);
        log.debug("O2C preferred language resolved. traceId={}, resolvedLanguage={}",
                TraceContext.getTraceId(), languageCode);
        return languageCode;
    }

    private SupportedLanguage getDefaultActiveLanguage() {
        log.debug("O2C loading default active language. traceId={}", TraceContext.getTraceId());
        return supportedLanguageRepository
                .findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc()
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.DEFAULT_LANGUAGE_NOT_CONFIGURED));
    }

    private String normalizeOptionalText(String value) {
        log.debug("O2C normalizing optional text. traceId={}, present={}", TraceContext.getTraceId(), value != null);
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AccountIdentifier getIdentifier(Party party) {
        Identifier identifier = party.getIdentifier();
        String identifierType = resolveIdentifierTypeForLookup(identifier.getType());
        log.debug("O2C loading account identifier. traceId={}, identifierType={}, identifierValuePresent={}",
                TraceContext.getTraceId(), identifierType, identifier.getValue() != null && !identifier.getValue().isBlank());

        AccountIdentifier accountIdentifier = accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        identifierType,
                        identifier.getValue(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        null,
                        Map.of("identifierValue", identifier.getValue())
                ));
        log.debug("O2C account identifier loaded. traceId={}, accountId={}, identifierType={}",
                TraceContext.getTraceId(), accountIdentifier.getAccountId(), accountIdentifier.getIdentifierType());
        return accountIdentifier;
    }

    private String resolveIdentifierTypeForLookup(IdentifierType identifierType) {
        log.debug("O2C resolving identifier type for lookup. traceId={}, identifierType={}",
                TraceContext.getTraceId(), identifierType);
        if (identifierType == IdentifierType.MSISDN) {
            return IdentifierType.MOBILE.name();
        }
        return identifierType.name();
    }

    private Account getActiveAccount(String accountId) {
        log.debug("O2C loading active account. traceId={}, accountId={}", TraceContext.getTraceId(), accountId);
        Account account = accountRepository
                .findByAccountIdAndStatus(accountId, Constants.ACCOUNT_STATUS_ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApplicationException(ErrorCodes.ACCOUNT_NOT_FOUND, "Account not found"));
        log.debug("O2C active account loaded. traceId={}, accountId={}, accountType={}",
                TraceContext.getTraceId(), account.getAccountId(), account.getAccountType());
        return account;
    }

    private void validateChannelAccount(Account account, AccountType requestedAccountType) {
        log.debug("O2C validating channel account. traceId={}, accountId={}, actualType={}, requestedType={}",
                TraceContext.getTraceId(), account.getAccountId(), account.getAccountType(), requestedAccountType);
        if (!account.getAccountType().equalsIgnoreCase(requestedAccountType.name())) {
            log.warn("O2C channel account validation failed. traceId={}, accountId={}, actualType={}, requestedType={}",
                    TraceContext.getTraceId(), account.getAccountId(), account.getAccountType(), requestedAccountType);
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CREDITOR_ACCOUNT_TYPE,
                    null,
                    Map.of(
                            "role", "CHANNEL",
                            "expectedType", requestedAccountType.name(),
                            "actualType", account.getAccountType()
                    )
            );
        }
        log.debug("O2C channel account validation completed. traceId={}, accountId={}",
                TraceContext.getTraceId(), account.getAccountId());
    }

    private Wallet getWallet(String accountId, String currency, String walletType, String role) {
        log.debug("O2C loading wallet. traceId={}, role={}, accountId={}, currency={}, walletType={}",
                TraceContext.getTraceId(), role, accountId, currency, walletType);
        Wallet wallet = walletRepository
                .findByAccountIdAndCurrencyAndWalletType(accountId, currency, walletType)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.WALLET_NOT_FOUND,
                        null,
                        Map.of("role", role, "currency", currency, "walletType", walletType)
                ));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            log.warn("O2C wallet validation failed: inactive wallet. traceId={}, role={}, walletId={}, status={}",
                    TraceContext.getTraceId(), role, wallet.getWalletId(), wallet.getStatus());
            throw new ApplicationException(PaymentErrorCode.INVALID_WALLET, null, Map.of("role", role));
        }
        if (Boolean.TRUE.equals(wallet.getIsLocked())) {
            log.warn("O2C wallet validation failed: locked wallet. traceId={}, role={}, walletId={}",
                    TraceContext.getTraceId(), role, wallet.getWalletId());
            throw new ApplicationException(PaymentErrorCode.WALLET_LOCKED, null, Map.of("role", role));
        }
        log.debug("O2C wallet loaded. traceId={}, role={}, walletId={}, accountId={}, currency={}, walletType={}",
                TraceContext.getTraceId(), role, wallet.getWalletId(), wallet.getAccountId(), wallet.getCurrency(), wallet.getWalletType());
        return wallet;
    }

    private String getRequiredServerInstance() {
        log.debug("O2C loading required server instance. traceId={}", TraceContext.getTraceId());
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            log.warn("O2C server instance missing. traceId={}", TraceContext.getTraceId());
            throw new IllegalStateException("server.instance is not configured");
        }
        log.debug("O2C server instance resolved. traceId={}, serverInstance={}",
                TraceContext.getTraceId(), serverInstance.trim());
        return serverInstance.trim();
    }

    private String resolveOperatorAccountId(String currency) {
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        log.debug("O2C resolving operator account. traceId={}, currency={}",
                TraceContext.getTraceId(), normalizedCurrency);
        String currencySpecificAccountId = propertyReader.getPropertyValue(
                OPERATOR_ACCOUNT_PROPERTY_PREFIX + normalizedCurrency
        );
        if (currencySpecificAccountId != null && !currencySpecificAccountId.isBlank()) {
            log.debug("O2C resolved currency-specific operator account. traceId={}, currency={}, operatorAccountId={}",
                    TraceContext.getTraceId(), normalizedCurrency, currencySpecificAccountId.trim());
            return currencySpecificAccountId.trim();
        }

        String defaultAccountId = propertyReader.getPropertyValue(OPERATOR_DEFAULT_ACCOUNT_PROPERTY);
        if (defaultAccountId != null && !defaultAccountId.isBlank()) {
            log.debug("O2C resolved default configured operator account. traceId={}, operatorAccountId={}",
                    TraceContext.getTraceId(), defaultAccountId.trim());
            return defaultAccountId.trim();
        }

        log.debug("O2C using hard-coded default operator account. traceId={}, operatorAccountId={}",
                TraceContext.getTraceId(), DEFAULT_OPERATOR_ACCOUNT_ID);
        return DEFAULT_OPERATOR_ACCOUNT_ID;
    }

    private void validateApprovalRequest(O2CApprovalRequest request) {
        log.debug("O2C validating approval request. traceId={}, requestNull={}",
                TraceContext.getTraceId(), request == null);
        if (request == null) {
            log.warn("O2C approval request validation failed: request body is null. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Request body cannot be null");
        }
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            log.warn("O2C approval request validation failed: transaction id missing. traceId={}", TraceContext.getTraceId());
            throw new ApplicationException(ErrorCodes.TXN_ID_MISSING, "Transaction id is required");
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            log.warn("O2C approval request validation failed: status missing. traceId={}, transactionId={}",
                    TraceContext.getTraceId(), request.getTransactionId());
            throw new ApplicationException(ErrorCodes.STATUS_MISSING, "Status is required");
        }
        log.debug("O2C approval request validation completed. traceId={}, transactionId={}, status={}",
                TraceContext.getTraceId(), request.getTransactionId(), request.getStatus());
    }

    private TransactionDetails getTransactionDetail(List<TransactionDetails> transactionDetails, Long sequenceNumber) {
        log.debug("O2C selecting transaction detail. traceId={}, sequenceNumber={}, detailCount={}",
                TraceContext.getTraceId(), sequenceNumber, transactionDetails.size());
        TransactionDetails detail = transactionDetails.stream()
                .filter(detail1 -> sequenceNumber.equals(detail1.getId().getTxnSequenceNumber()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.TRANSACTION_DETAIL_NOT_FOUND,
                        "Transaction detail not found"
                ));
        log.debug("O2C transaction detail selected. traceId={}, transactionId={}, sequenceNumber={}, entryType={}, walletNumber={}",
                TraceContext.getTraceId(), detail.getId().getTransactionId(), sequenceNumber,
                detail.getEntryType(), detail.getWalletNumber());
        return detail;
    }

    private Wallet getWalletById(String walletNumber, String role) {
        log.debug("O2C loading wallet by id. traceId={}, role={}, walletNumber={}",
                TraceContext.getTraceId(), role, walletNumber);
        if (walletNumber == null || walletNumber.isBlank()) {
            log.warn("O2C wallet lookup failed: wallet number missing. traceId={}, role={}",
                    TraceContext.getTraceId(), role);
            throw new ApplicationException(PaymentErrorCode.WALLET_NOT_FOUND, null, Map.of("role", role));
        }
        Long walletId;
        try {
            walletId = Long.valueOf(walletNumber);
        } catch (NumberFormatException ex) {
            log.warn("O2C wallet lookup failed: wallet number invalid. traceId={}, role={}, walletNumber={}",
                    TraceContext.getTraceId(), role, walletNumber);
            throw new ApplicationException(PaymentErrorCode.WALLET_NOT_FOUND, null, Map.of("role", role));
        }
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.WALLET_NOT_FOUND,
                        null,
                        Map.of("role", role, "walletId", walletNumber)
                ));
        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            log.warn("O2C wallet by id validation failed: inactive wallet. traceId={}, role={}, walletId={}, status={}",
                    TraceContext.getTraceId(), role, wallet.getWalletId(), wallet.getStatus());
            throw new ApplicationException(PaymentErrorCode.INVALID_WALLET, null, Map.of("role", role));
        }
        if (Boolean.TRUE.equals(wallet.getIsLocked())) {
            log.warn("O2C wallet by id validation failed: locked wallet. traceId={}, role={}, walletId={}",
                    TraceContext.getTraceId(), role, wallet.getWalletId());
            throw new ApplicationException(PaymentErrorCode.WALLET_LOCKED, null, Map.of("role", role));
        }
        log.debug("O2C wallet by id loaded. traceId={}, role={}, walletId={}, accountId={}, currency={}, walletType={}",
                TraceContext.getTraceId(), role, wallet.getWalletId(), wallet.getAccountId(), wallet.getCurrency(), wallet.getWalletType());
        return wallet;
    }

    private BigDecimal toDisplayAmount(BigDecimal storedAmount) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        BigDecimal displayAmount = storedAmount.divide(currencyFactor, 2, RoundingMode.HALF_UP);
        log.debug("O2C converted stored amount to display amount. traceId={}, storedAmount={}, displayAmount={}, currencyFactor={}",
                TraceContext.getTraceId(), storedAmount, displayAmount, currencyFactor);
        return displayAmount;
    }

    private void recordTransactionInitiator(String transactionId, String initiatorAccountId) {
        log.debug("O2C recording transaction initiator. traceId={}, transactionId={}, initiatorAccountId={}",
                TraceContext.getTraceId(), transactionId, initiatorAccountId);
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        if (transaction == null || initiatorAccountId == null || initiatorAccountId.isBlank()) {
            log.warn("O2C skipped recording transaction initiator. traceId={}, transactionId={}, transactionFound={}, initiatorAccountIdPresent={}",
                    TraceContext.getTraceId(), transactionId, transaction != null,
                    initiatorAccountId != null && !initiatorAccountId.isBlank());
            return;
        }
        transaction.setCreatedBy(initiatorAccountId);
        // transaction.setModifiedBy(initiatorAccountId);
        transactionsRepository.save(transaction);
        log.debug("O2C transaction initiator recorded. traceId={}, transactionId={}, initiatorAccountId={}",
                TraceContext.getTraceId(), transactionId, initiatorAccountId);
    }

    private void recordTransactionModifier(String transactionId, String modifierAccountId) {
        log.debug("O2C recording transaction modifier. traceId={}, transactionId={}, modifierAccountId={}",
                TraceContext.getTraceId(), transactionId, modifierAccountId);
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        if (transaction == null || modifierAccountId == null || modifierAccountId.isBlank()) {
            log.warn("O2C skipped recording transaction modifier. traceId={}, transactionId={}, transactionFound={}, modifierAccountIdPresent={}",
                    TraceContext.getTraceId(), transactionId, transaction != null,
                    modifierAccountId != null && !modifierAccountId.isBlank());
            return;
        }
        transaction.setModifiedBy(modifierAccountId);
        transactionsRepository.save(transaction);
        log.debug("O2C transaction modifier recorded. traceId={}, transactionId={}, modifierAccountId={}",
                TraceContext.getTraceId(), transactionId, modifierAccountId);
    }

    private void updateApproveOrRejectComments(String transactionId, String comments) {
        log.debug("O2C updating approve/reject comments. traceId={}, transactionId={}, commentsPresent={}",
                TraceContext.getTraceId(), transactionId, comments != null && !comments.isBlank());
        if (comments == null || comments.isBlank()) {
            log.debug("O2C approve/reject comments skipped because comments are blank. traceId={}, transactionId={}",
                    TraceContext.getTraceId(), transactionId);
            return;
        }
        transactionsRepository.updateApproveOrRejectComments(transactionId, comments);
        log.debug("O2C approve/reject comments updated. traceId={}, transactionId={}",
                TraceContext.getTraceId(), transactionId);
    }

    private AccountIdentifier buildAccountIdentifier(String accountId) {
        log.debug("O2C building account identifier. traceId={}, accountId={}", TraceContext.getTraceId(), accountId);
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(IdentifierType.ACCOUNT_ID.name());
        identifier.setIdentifierValue(accountId);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        log.debug("O2C account identifier built. traceId={}, accountId={}, identifierType={}",
                TraceContext.getTraceId(), accountId, identifier.getIdentifierType());
        return identifier;
    }

    private void updateOptionalTransactionFields(String transactionId, O2CPaymentRequest request) {
        log.debug("O2C updating optional transaction fields. traceId={}, transactionId={}, metadataPresent={}, additionalInfoPresent={}, paymentReferencePresent={}, commentsPresent={}",
                TraceContext.getTraceId(),
                transactionId,
                request.getMetadata() != null && !request.getMetadata().isEmpty(),
                request.getAdditionalInfo() != null && !request.getAdditionalInfo().isEmpty(),
                request.getPaymentReference() != null,
                request.getComments() != null);
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        if (transaction == null) {
            log.warn("O2C optional transaction fields skipped because transaction was not found. traceId={}, transactionId={}",
                    TraceContext.getTraceId(), transactionId);
            return;
        }

        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            log.debug("O2C updating metadata. traceId={}, transactionId={}", TraceContext.getTraceId(), transactionId);
            JSONObject metadata = mergeJson(
                    transaction.getMetadata(),
                    new JSONObject(request.getMetadata()),
                    "metadata",
                    transactionId
            );
            transaction.setMetadata(metadata.toString());
        }
        if (request.getAdditionalInfo() != null && !request.getAdditionalInfo().isEmpty()) {
            log.debug("O2C updating additional info. traceId={}, transactionId={}", TraceContext.getTraceId(), transactionId);
            JSONObject additionalInfo = mergeJson(
                    transaction.getAdditionalInfo(),
                    new JSONObject(request.getAdditionalInfo()),
                    "additionalInfo",
                    transactionId
            );
            transaction.setAdditionalInfo(additionalInfo.toString());
        }
        if (request.getPaymentReference() != null) {
            log.debug("O2C updating payment reference. traceId={}, transactionId={}", TraceContext.getTraceId(), transactionId);
            transaction.setPaymentReference(request.getPaymentReference());
        }
        if (request.getComments() != null) {
            log.debug("O2C updating comments. traceId={}, transactionId={}", TraceContext.getTraceId(), transactionId);
            transaction.setComments(request.getComments());
        }

        transactionsRepository.save(transaction);
        log.debug("O2C optional transaction fields updated. traceId={}, transactionId={}",
                TraceContext.getTraceId(), transactionId);
    }

    private JSONObject mergeJson(String existingValue, JSONObject newValue, String fieldName, String transactionId) {
        JSONObject mergedValue = new JSONObject();
        if (existingValue != null && !existingValue.isBlank()) {
            try {
                mergedValue = new JSONObject(existingValue);
            } catch (Exception ex) {
                log.warn("O2C existing optional JSON field is invalid and will be replaced. traceId={}, transactionId={}, fieldName={}",
                        TraceContext.getTraceId(), transactionId, fieldName);
            }
        }

        for (String key : newValue.keySet()) {
            mergedValue.put(key, newValue.get(key));
        }
        return mergedValue;
    }
}
