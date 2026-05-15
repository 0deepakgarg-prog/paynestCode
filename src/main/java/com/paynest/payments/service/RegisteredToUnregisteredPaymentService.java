package com.paynest.payments.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.security.JWTUtils;
import com.paynest.config.tenant.TenantTime;
import com.paynest.config.tenant.TraceContext;
import com.paynest.enums.AccountType;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.RegisteredToUnregisteredPaymentRequest;
import com.paynest.payments.dto.RegisteredToUnregisteredPaymentResponse;
import com.paynest.payments.entity.Passcode;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.PasscodeStatus;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.repository.PasscodeRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class RegisteredToUnregisteredPaymentService {

    static final String OPERATION_NAME = "R2U";
    private static final String TRANSACTION_PREFIX = "RU";
    private static final int MAX_PASSCODE_GENERATION_ATTEMPTS = 10;
    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final String HOLDING_WALLET_TYPE = "HOLDING";

    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final PropertyReader propertyReader;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;
    private final AuthService authService;
    private final PasscodeRepository passcodeRepository;
    private final PasscodeGenerator passcodeGenerator;
    private final PasscodeSmsNotificationService passcodeSmsNotificationService;

    public RegisteredToUnregisteredPaymentService(
            AccountIdentifierRepository accountIdentifierRepository,
            AccountRepository accountRepository,
            WalletRepository walletRepository,
            PropertyReader propertyReader,
            TransactionsService transactionsService,
            BalanceService balanceService,
            AuthService authService,
            PasscodeRepository passcodeRepository,
            PasscodeGenerator passcodeGenerator,
            PasscodeSmsNotificationService passcodeSmsNotificationService
    ) {
        this.accountIdentifierRepository = accountIdentifierRepository;
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
        this.propertyReader = propertyReader;
        this.transactionsService = transactionsService;
        this.balanceService = balanceService;
        this.authService = authService;
        this.passcodeRepository = passcodeRepository;
        this.passcodeGenerator = passcodeGenerator;
        this.passcodeSmsNotificationService = passcodeSmsNotificationService;
    }

    public RegisteredToUnregisteredPaymentResponse processPayment(
            RegisteredToUnregisteredPaymentRequest request,
            boolean validateJWT
    ) {
        validateRequest(request);
        normalizeRequest(request);

        AccountIdentifier debitorIdentifier = getIdentifier(request.getDebitor());
        ensureReceiverIsUnregistered(request.getReceiverMsisdn());
        validateJwtAccess(validateJWT, debitorIdentifier, request.getDebitor().getAuthentication());

        Account debitorAccount = getAccount(debitorIdentifier);
        validateAccountType(debitorAccount, AccountType.SUBSCRIBER, InitiatedBy.DEBITOR.name());

        authService.validateAuthentication(
                request.getDebitor().getAuthentication().getValue(),
                request.getDebitor().getAuthentication().getType(),
                debitorIdentifier
        );

        Wallet debitorWallet = getWallet(
                debitorAccount.getAccountId(),
                request.getDebitor(),
                request.getTransaction().getCurrency(),
                InitiatedBy.DEBITOR.name()
        );
        Wallet holdingWallet = getHoldingWallet(request.getTransaction().getCurrency());
        AccountIdentifier holdingIdentifier = systemIdentifier(
                holdingWallet.getAccountId(),
                request.getReceiverMsisdn(),
                IdentifierType.MSISDN.name()
        );

        String transactionId = IdGenerator.generateTransactionId(
                TRANSACTION_PREFIX,
                getRequiredServerInstance()
        );

        try {
            transactionsService.generateTransactionRecord(
                    transactionId,
                    request.getTransaction().getAmount(),
                    request.getRequestGateway().name(),
                    OPERATION_NAME,
                    request.getPreferredLang(),
                    debitorIdentifier,
                    holdingIdentifier,
                    debitorAccount.getAccountType(),
                    "SYSTEM",
                    debitorWallet,
                    holdingWallet,
                    request.getInitiatedBy(),
                    request.getPaymentReference(),
                    request.getComments()
            );

            balanceService.transferWalletAmount(
                    debitorWallet,
                    holdingWallet,
                    request.getTransaction().getAmount(),
                    OPERATION_NAME,
                    request.getInitiatedBy(),
                    transactionId
            );
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(transactionId);
        }

        String passcodeValue = generateUniquePasscode();
        Passcode passcode = new Passcode();
        passcode.setTransactionId(transactionId);
        passcode.setAmount(toStoredAmount(request.getTransaction().getAmount()));
        passcode.setCurrency(request.getTransaction().getCurrency());
        passcode.setUnregisteredMsisdn(request.getReceiverMsisdn());
        passcode.setFirstName(request.getReceiverFirstName());
        passcode.setLastName(request.getReceiverLastName());
        passcode.setKycDocumentId(request.getReceiverKycDocumentId());
        passcode.setSenderMsisdn(debitorIdentifier.getIdentifierValue());
        passcode.setSenderAccountId(debitorAccount.getAccountId());
        passcode.setPasscode(passcodeValue);
        passcode.setStatus(PasscodeStatus.PENDING);
        passcode.setField1(request.getField1());
        passcode.setField2(request.getField2());
        passcode.setField3(request.getField3());
        passcode.setField4(request.getField4());
        passcode.setField5(request.getField5());
        passcodeRepository.save(passcode);

        passcodeSmsNotificationService.sendPasscode(
                debitorIdentifier.getIdentifierValue(),
                request.getReceiverMsisdn(),
                passcodeValue
        );

        return RegisteredToUnregisteredPaymentResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(OPERATION_NAME)
                .code("PAYMENT_SUCCESS")
                .message("Payment moved to holding wallet")
                .timestamp(TenantTime.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .currency(request.getTransaction().getCurrency())
                .receiverMsisdn(request.getReceiverMsisdn())
                .build();
    }

    private void validateRequest(RegisteredToUnregisteredPaymentRequest request) {
        if (request == null || request.getTransaction() == null) {
            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }
        if (request.getRequestGateway() == null) {
            throw new ApplicationException(PaymentErrorCode.REQUEST_GATEWAY_MISSING);
        }
        if (request.getInitiatedBy() != InitiatedBy.DEBITOR) {
            throw new ApplicationException(PaymentErrorCode.INVALID_INITIATOR);
        }
        validateParty(request.getDebitor());
        if (request.getReceiverMsisdn() == null || request.getReceiverMsisdn().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_VALUE_MISSING);
        }
        if (request.getTransaction().getAmount() == null
                || request.getTransaction().getAmount().signum() <= 0) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT);
        }
        if (request.getTransaction().getAmount().scale() > 2) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT_SCALE);
        }
        if (request.getTransaction().getCurrency() == null
                || request.getTransaction().getCurrency().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.CURRENCY_MISSING);
        }
    }

    private void validateParty(Party party) {
        if (party == null) {
            throw new ApplicationException(PaymentErrorCode.DEBTOR_MISSING);
        }
        if (party.getAccountType() != AccountType.SUBSCRIBER) {
            throw new ApplicationException(PaymentErrorCode.INVALID_DEBITOR_USER_TYPE);
        }
        if (party.getIdentifier() == null || party.getIdentifier().getType() == null
                || party.getIdentifier().getValue() == null || party.getIdentifier().getValue().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_MISSING);
        }
        if (party.getWalletType() == null) {
            throw new ApplicationException(PaymentErrorCode.WALLET_TYPE_MISSING);
        }
        if (party.getAuthentication() == null || party.getAuthentication().getType() == null
                || party.getAuthentication().getValue() == null || party.getAuthentication().getValue().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private void normalizeRequest(RegisteredToUnregisteredPaymentRequest request) {
        request.setOperationType(OPERATION_NAME);
        request.setPreferredLang(request.getPreferredLang() == null || request.getPreferredLang().isBlank()
                ? "en"
                : request.getPreferredLang().trim().toLowerCase(Locale.ROOT));
        request.getTransaction().setCurrency(request.getTransaction().getCurrency().trim().toUpperCase(Locale.ROOT));
        request.setReceiverMsisdn(request.getReceiverMsisdn().trim());
        request.setReceiverFirstName(normalizeOptionalText(request.getReceiverFirstName()));
        request.setReceiverLastName(normalizeOptionalText(request.getReceiverLastName()));
        request.setReceiverKycDocumentId(normalizeOptionalText(request.getReceiverKycDocumentId()));
        request.getDebitor().getIdentifier().setValue(request.getDebitor().getIdentifier().getValue().trim());
        request.setPaymentReference(normalizeOptionalText(request.getPaymentReference()));
        request.setComments(normalizeOptionalText(request.getComments()));
        request.setField1(normalizeOptionalText(request.getField1()));
        request.setField2(normalizeOptionalText(request.getField2()));
        request.setField3(normalizeOptionalText(request.getField3()));
        request.setField4(normalizeOptionalText(request.getField4()));
        request.setField5(normalizeOptionalText(request.getField5()));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void ensureReceiverIsUnregistered(String receiverMsisdn) {
        accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        IdentifierType.MOBILE.name(),
                        receiverMsisdn,
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .ifPresent(identifier -> {
                    throw new ApplicationException(
                            PaymentErrorCode.UNREGISTERED_PAYEE_ALREADY_EXISTS,
                            null,
                            Map.of("receiverMsisdn", receiverMsisdn)
                    );
                });
    }

    private AccountIdentifier getIdentifier(Party party) {
        Identifier identifier = party.getIdentifier();
        String identifierType = identifier.getType() == IdentifierType.MSISDN
                ? IdentifierType.MOBILE.name()
                : identifier.getType().name();
        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        identifierType,
                        identifier.getValue(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND));
    }

    private Account getAccount(AccountIdentifier identifier) {
        return accountRepository
                .findByAccountIdAndStatus(identifier.getAccountId(), Constants.ACCOUNT_STATUS_ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateAccountType(Account account, AccountType expectedType, String role) {
        if (!account.getAccountType().equalsIgnoreCase(expectedType.name())) {
            throw new ApplicationException(
                    InitiatedBy.DEBITOR.name().equals(role)
                            ? PaymentErrorCode.INVALID_DEBITOR_ACCOUNT_TYPE
                            : PaymentErrorCode.INVALID_CREDITOR_ACCOUNT_TYPE
            );
        }
    }

    private Wallet getWallet(String accountId, Party party, String currency, String role) {
        return walletRepository
                .findByAccountIdAndCurrencyAndWalletType(accountId, currency, party.getWalletType().name())
                .filter(wallet -> Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus()))
                .filter(wallet -> !Boolean.TRUE.equals(wallet.getIsLocked()))
                .orElseThrow(() -> new ApplicationException(
                        InitiatedBy.DEBITOR.name().equals(role)
                                ? PaymentErrorCode.WALLET_NOT_FOUND
                                : PaymentErrorCode.INVALID_WALLET
                ));
    }

    private Wallet getHoldingWallet(String currency) {
        return walletRepository
                .findByAccountIdAndCurrencyAndWalletType(
                        SYSTEM_ACCOUNT_ID,
                        currency,
                        HOLDING_WALLET_TYPE
                )
                .filter(wallet -> Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus()))
                .filter(wallet -> !Boolean.TRUE.equals(wallet.getIsLocked()))
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.WALLET_NOT_FOUND));
    }

    private void validateJwtAccess(
            boolean validateJWT,
            AccountIdentifier debitorIdentifier,
            Authentication requestedAuthentication
    ) {
        if (!validateJWT) {
            return;
        }
        if (!debitorIdentifier.getAccountId().equalsIgnoreCase(JWTUtils.getCurrentAccountId())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRIVILEGES);
        }
        if (!AccountType.SUBSCRIBER.name().equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRIVILEGES);
        }
        if (!requestedAuthentication.getType().name().equalsIgnoreCase(JWTUtils.getCurrentAuthType())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AUTH_TYPE);
        }
    }

    private String generateUniquePasscode() {
        for (int attempt = 0; attempt < MAX_PASSCODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = passcodeGenerator.generate();
            if (candidate != null
                    && candidate.matches("\\d{10}")
                    && !passcodeRepository.existsByPasscode(candidate)) {
                return candidate;
            }
        }
        throw new ApplicationException(PaymentErrorCode.PASSCODE_GENERATION_FAILED);
    }

    private java.math.BigDecimal toStoredAmount(java.math.BigDecimal displayAmount) {
        return displayAmount
                .multiply(new java.math.BigDecimal(getRequiredCurrencyFactor()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String getRequiredCurrencyFactor() {
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        if (currencyFactor == null || currencyFactor.isBlank()) {
            throw new IllegalStateException("currency.factor is not configured");
        }
        return currencyFactor.trim();
    }

    private AccountIdentifier systemIdentifier(String accountId, String value, String type) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierValue(value);
        identifier.setIdentifierType(type);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }
}
