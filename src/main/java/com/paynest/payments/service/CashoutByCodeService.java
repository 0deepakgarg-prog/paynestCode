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
import com.paynest.payments.dto.CashoutByCodeRequest;
import com.paynest.payments.dto.CashoutByCodeResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
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
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Transactional
public class CashoutByCodeService {

    static final String OPERATION_NAME = "CASHOUT_BY_CODE";
    private static final String TRANSACTION_PREFIX = "CC";
    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final String HOLDING_WALLET_TYPE = "HOLDING";

    private final PasscodeRepository passcodeRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final PropertyReader propertyReader;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;
    private final AuthService authService;

    public CashoutByCodeService(
            PasscodeRepository passcodeRepository,
            AccountIdentifierRepository accountIdentifierRepository,
            AccountRepository accountRepository,
            WalletRepository walletRepository,
            PropertyReader propertyReader,
            TransactionsService transactionsService,
            BalanceService balanceService,
            AuthService authService
    ) {
        this.passcodeRepository = passcodeRepository;
        this.accountIdentifierRepository = accountIdentifierRepository;
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
        this.propertyReader = propertyReader;
        this.transactionsService = transactionsService;
        this.balanceService = balanceService;
        this.authService = authService;
    }

    public CashoutByCodeResponse processCashout(CashoutByCodeRequest request, boolean validateJWT) {
        validateRequest(request);
        normalizeRequest(request);

        Passcode passcode = passcodeRepository
                .findByPasscodeAndUnregisteredMsisdnAndStatus(
                        request.getPasscode(),
                        request.getMsisdn(),
                        PasscodeStatus.PENDING
                )
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.PASSCODE_NOT_FOUND));

        AccountIdentifier agentIdentifier = getIdentifier(request.getAgent());
        validateJwtAccess(validateJWT, agentIdentifier, request.getAgent().getAuthentication());
        Account agentAccount = getAccount(agentIdentifier);
        validateAccountType(agentAccount, AccountType.AGENT);
        authService.validateAuthentication(
                request.getAgent().getAuthentication().getValue(),
                request.getAgent().getAuthentication().getType(),
                agentIdentifier
        );

        Wallet holdingWallet = getHoldingWallet(passcode.getCurrency());
        Wallet agentWallet = getWallet(
                agentAccount.getAccountId(),
                request.getAgent(),
                passcode.getCurrency()
        );
        BigDecimal cashoutAmount = toDisplayAmount(passcode.getAmount());

        AccountIdentifier holdingIdentifier = systemIdentifier(
                holdingWallet.getAccountId(),
                passcode.getUnregisteredMsisdn(),
                IdentifierType.MSISDN.name()
        );

        String transactionId = IdGenerator.generateTransactionId(
                TRANSACTION_PREFIX,
                getRequiredServerInstance()
        );

        try {
            transactionsService.generateTransactionRecord(
                    transactionId,
                    cashoutAmount,
                    request.getRequestGateway().name(),
                    OPERATION_NAME,
                    request.getPreferredLang(),
                    holdingIdentifier,
                    agentIdentifier,
                    "SYSTEM",
                    agentAccount.getAccountType(),
                    holdingWallet,
                    agentWallet,
                    request.getInitiatedBy(),
                    request.getPaymentReference(),
                    request.getComments()
            );

            balanceService.transferWalletAmount(
                    holdingWallet,
                    agentWallet,
                    cashoutAmount,
                    OPERATION_NAME,
                    request.getInitiatedBy(),
                    transactionId
            );
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(transactionId);
        }

        passcode.setStatus(PasscodeStatus.REDEEMED);
        passcode.setCashoutTransactionId(transactionId);
        passcode.setRedeemedOn(TenantTime.now());
        passcodeRepository.save(passcode);

        return CashoutByCodeResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(OPERATION_NAME)
                .code("PAYMENT_SUCCESS")
                .message("Cash-out by code successful")
                .timestamp(TenantTime.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(cashoutAmount)
                .currency(passcode.getCurrency())
                .msisdn(passcode.getUnregisteredMsisdn())
                .build();
    }

    private void validateRequest(CashoutByCodeRequest request) {
        if (request == null) {
            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }
        if (request.getRequestGateway() == null) {
            throw new ApplicationException(PaymentErrorCode.REQUEST_GATEWAY_MISSING);
        }
        if (request.getInitiatedBy() != InitiatedBy.CREDITOR) {
            throw new ApplicationException(PaymentErrorCode.INVALID_INITIATOR);
        }
        validateAgent(request.getAgent());
        if (request.getMsisdn() == null || request.getMsisdn().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_VALUE_MISSING);
        }
        if (request.getPasscode() == null || !request.getPasscode().matches("\\d{10}")) {
            throw new ApplicationException(PaymentErrorCode.PASSCODE_NOT_FOUND);
        }
    }

    private void validateAgent(Party party) {
        if (party == null) {
            throw new ApplicationException(PaymentErrorCode.CREDITOR_MISSING);
        }
        if (party.getAccountType() != AccountType.AGENT) {
            throw new ApplicationException(PaymentErrorCode.INVALID_CREDITOR_USER_TYPE);
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

    private void normalizeRequest(CashoutByCodeRequest request) {
        request.setOperationType(OPERATION_NAME);
        request.setPreferredLang(request.getPreferredLang() == null || request.getPreferredLang().isBlank()
                ? "en"
                : request.getPreferredLang().trim().toLowerCase(Locale.ROOT));
        request.setMsisdn(request.getMsisdn().trim());
        request.setPasscode(request.getPasscode().trim());
        request.getAgent().getIdentifier().setValue(request.getAgent().getIdentifier().getValue().trim());
        request.setPaymentReference(normalizeOptionalText(request.getPaymentReference()));
        request.setComments(normalizeOptionalText(request.getComments()));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private void validateAccountType(Account account, AccountType expectedType) {
        if (!account.getAccountType().equalsIgnoreCase(expectedType.name())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_CREDITOR_ACCOUNT_TYPE);
        }
    }

    private Wallet getWallet(String accountId, Party party, String currency) {
        return walletRepository
                .findByAccountIdAndCurrencyAndWalletType(accountId, currency, party.getWalletType().name())
                .filter(wallet -> Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus()))
                .filter(wallet -> !Boolean.TRUE.equals(wallet.getIsLocked()))
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.WALLET_NOT_FOUND));
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
            AccountIdentifier agentIdentifier,
            Authentication requestedAuthentication
    ) {
        if (!validateJWT) {
            return;
        }
        if (!agentIdentifier.getAccountId().equalsIgnoreCase(JWTUtils.getCurrentAccountId())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRIVILEGES);
        }
        if (!AccountType.AGENT.name().equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRIVILEGES);
        }
        if (!requestedAuthentication.getType().name().equalsIgnoreCase(JWTUtils.getCurrentAuthType())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AUTH_TYPE);
        }
    }

    private AccountIdentifier systemIdentifier(String accountId, String value, String type) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierValue(value);
        identifier.setIdentifierType(type);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private BigDecimal toDisplayAmount(BigDecimal storedAmount) {
        return storedAmount.divide(
                new BigDecimal(getRequiredCurrencyFactor()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private String getRequiredCurrencyFactor() {
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        if (currencyFactor == null || currencyFactor.isBlank()) {
            throw new IllegalStateException("currency.factor is not configured");
        }
        return currencyFactor.trim();
    }

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }
}
