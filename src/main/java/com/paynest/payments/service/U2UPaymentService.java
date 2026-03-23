package com.paynest.payments.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.validation.BasePaymentRequestValidator;
import com.paynest.config.tenant.TraceContext;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountAuth;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.AccountType;
import com.paynest.users.repository.AccountAuthRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class U2UPaymentService {

    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountAuthRepository accountAuthRepository;
    private final BasePaymentRequestValidator basePaymentRequestValidator;
    private final PropertyReader propertyReader;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;

    public U2UPaymentService(
            WalletRepository walletRepository,
            AccountRepository accountRepository,
            AccountAuthRepository accountAuthRepository,
            AccountIdentifierRepository accountIdentifierRepository,
            BasePaymentRequestValidator basePaymentRequestValidator,
            PropertyReader propertyReader,
            TransactionsService transactionsService,
            BalanceService balanceService
    ) {
        this.walletRepository = walletRepository;
        this.accountRepository = accountRepository;
        this.accountAuthRepository = accountAuthRepository;
        this.accountIdentifierRepository = accountIdentifierRepository;
        this.basePaymentRequestValidator = basePaymentRequestValidator;
        this.propertyReader = propertyReader;
        this.transactionsService = transactionsService;
        this.balanceService = balanceService;
    }

    public BasePaymentResponse processPayment(U2UPaymentRequest request) {

        log.info("Processing U2U payment request. traceId={}", TraceContext.getTraceId());

        String transactionId = IdGenerator.generateTransactionId("UU");
        AccountIdentifier debtorIdentifier = null;
        AccountIdentifier creditorIdentifier = null;
        Wallet debtorWallet = null;
        Wallet creditorWallet = null;
        boolean transactionRecorded = false;

        try {
            basePaymentRequestValidator.validate(request);

            validateParty(request.getDebitor(), InitiatedBy.DEBITOR);
            validateParty(request.getCreditor(), InitiatedBy.CREDITOR);

            debtorIdentifier = validateIdentifierMapping(request.getDebitor());
            creditorIdentifier = validateIdentifierMapping(request.getCreditor());

            Account debtorAccount = identifyAccountFromAccountId(debtorIdentifier);
            Account creditorAccount = identifyAccountFromAccountId(creditorIdentifier);

            if (!debtorAccount.getAccountType()
                    .equalsIgnoreCase(request.getDebitor().getAccountType().name())) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_DEBTOR_ACCOUNT_TYPE,
                        "Debtor account type mismatch"
                );
            }

            if (!creditorAccount.getAccountType()
                    .equalsIgnoreCase(request.getCreditor().getAccountType().name())) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_CREDITOR_ACCOUNT_TYPE,
                        "Creditor account type mismatch"
                );
            }

            debtorWallet = getDefaultWallet(debtorAccount.getAccountId(), "DEBTOR");
            creditorWallet = getDefaultWallet(creditorAccount.getAccountId(), "CREDITOR");

            transactionsService.generateTransactionRecord(
                    transactionId,
                    request.getTransaction().getAmount(),
                    "MOBILE",
                    request.getOperationType(),
                    debtorIdentifier,
                    creditorIdentifier,
                    debtorWallet,
                    creditorWallet,
                    request.getInitiatedBy()
            );
            transactionRecorded = true;

            updateTrackingFields(transactionId, request);

            if (InitiatedBy.CREDITOR == request.getInitiatedBy()) {
                //
            } else if (InitiatedBy.DEBITOR == request.getInitiatedBy()) {
                AccountAuth debitorAccountAuth = getAuthorizationRecord(debtorIdentifier);
                boolean isAuthSuccessful = IdGenerator.verifyPin(
                        request.getDebitor().getAuthentication().getValue(),
                        debitorAccountAuth.getAuthHash(),
                        debitorAccountAuth.getAuthValue()
                );
                if (!isAuthSuccessful) {

                    int attempts = debitorAccountAuth.getFailedAttempts() == null
                            ? 1
                            : debitorAccountAuth.getFailedAttempts() + 1;

                    debitorAccountAuth.setFailedAttempts(attempts);
                    debitorAccountAuth.setLastFailedAt(LocalDateTime.now());

                    int maxInvalidAuthAttempts = Integer.parseInt(
                            propertyReader.getPropertyValue("max.allowed.invalid.auth.attempts"));

                    if (attempts >= maxInvalidAuthAttempts) {

                        debitorAccountAuth.setStatus("LOCKED");
                        debtorAccount.setStatus("LOCKED");

                        accountAuthRepository.save(debitorAccountAuth);
                        accountRepository.save(debtorAccount);

                        throw new ApplicationException(
                                ErrorCodes.INVALID_PIN,
                                "Maximum invalid PIN attempts reached. Wallet locked."
                        );
                    }

                    accountAuthRepository.save(debitorAccountAuth);

                    throw new ApplicationException(
                            ErrorCodes.INVALID_PIN,
                            "Invalid transaction PIN"
                    );
                }
            } else {
                throw new ApplicationException(
                        ErrorCodes.INVALID_INITIATOR,
                        "Initiator " + request.getInitiatedBy() + " not allowed"
                );
            }

            balanceService.transferWalletAmount(
                    debtorWallet,
                    creditorWallet,
                    request.getTransaction().getAmount(),
                    request.getOperationType(),
                    transactionId
            );

            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.SUCCESS)
                    .operationType(request.getOperationType())
                    .code("PAYMENT_SUCCESS")
                    .message("U2U Payment Successful")
                    .timestamp(Instant.now())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transactionId)
                    .amount(request.getTransaction().getAmount())
                    .currency(request.getTransaction().getCurrency())
                    .build();
        } catch (ApplicationException ex) {
            persistFailedTrackingRecord(
                    transactionRecorded,
                    transactionId,
                    request,
                    debtorIdentifier,
                    creditorIdentifier,
                    debtorWallet,
                    creditorWallet,
                    ex.getErrorCode()
            );
            throw ex;
        } catch (Exception ex) {
            persistFailedTrackingRecord(
                    transactionRecorded,
                    transactionId,
                    request,
                    debtorIdentifier,
                    creditorIdentifier,
                    debtorWallet,
                    creditorWallet,
                    ErrorCodes.SYSTEM_ERROR
            );
            throw ex;
        }
    }

    private void persistFailedTrackingRecord(
            boolean transactionRecorded,
            String transactionId,
            U2UPaymentRequest request,
            AccountIdentifier debtorIdentifier,
            AccountIdentifier creditorIdentifier,
            Wallet debtorWallet,
            Wallet creditorWallet,
            String errorCode
    ) {
        String actorAccountId = resolveActorAccountId(request, debtorIdentifier, creditorIdentifier);
        if (transactionRecorded) {
            transactionsService.updateFailedTransactionRecord(transactionId, errorCode, actorAccountId);
            return;
        }

        transactionsService.generateFailedTransactionRecord(
                transactionId,
                request != null && request.getTransaction() != null ? request.getTransaction().getAmount() : null,
                "MOBILE",
                request != null ? request.getOperationType() : null,
                buildTrackingIdentifier(request != null ? request.getDebitor() : null, debtorIdentifier, "DEBTOR"),
                buildTrackingIdentifier(request != null ? request.getCreditor() : null, creditorIdentifier, "CREDITOR"),
                debtorWallet,
                creditorWallet,
                request != null ? request.getInitiatedBy() : null,
                errorCode
        );
        updateTrackingFields(transactionId, request);
    }

    private void updateTrackingFields(String transactionId, U2UPaymentRequest request) {
        if (request == null) {
            return;
        }

        if (request.getMetadata() != null) {
            transactionsService.updateMetadata(transactionId, new JSONObject(request.getMetadata()));
        }
        if (request.getAdditionalInfo() != null) {
            transactionsService.updateAdditionalInfo(transactionId, new JSONObject(request.getAdditionalInfo()));
        }
        transactionsService.updatePaymentReference(transactionId, request.getPaymentReference());
        transactionsService.updateComments(transactionId, request.getComments());
    }

    private AccountIdentifier buildTrackingIdentifier(
            Party party,
            AccountIdentifier resolvedIdentifier,
            String fallbackRole
    ) {
        if (resolvedIdentifier != null) {
            return resolvedIdentifier;
        }

        AccountIdentifier trackingIdentifier = new AccountIdentifier();
        String identifierValue = "UNKNOWN_" + fallbackRole;
        String identifierType = "UNKNOWN";

        if (party != null && party.getIdentifier() != null) {
            if (party.getIdentifier().getValue() != null && !party.getIdentifier().getValue().isBlank()) {
                identifierValue = party.getIdentifier().getValue();
            }
            if (party.getIdentifier().getType() != null) {
                identifierType = party.getIdentifier().getType().name();
            }
        }

        trackingIdentifier.setAccountId(identifierValue);
        trackingIdentifier.setIdentifierType(identifierType);
        trackingIdentifier.setIdentifierValue(identifierValue);
        return trackingIdentifier;
    }

    private String resolveActorAccountId(
            U2UPaymentRequest request,
            AccountIdentifier debtorIdentifier,
            AccountIdentifier creditorIdentifier
    ) {
        if (request != null && request.getInitiatedBy() == InitiatedBy.CREDITOR && creditorIdentifier != null) {
            return creditorIdentifier.getAccountId();
        }
        if (debtorIdentifier != null) {
            return debtorIdentifier.getAccountId();
        }
        if (creditorIdentifier != null) {
            return creditorIdentifier.getAccountId();
        }
        return "SYSTEM";
    }

    private void validateParty(Party party, InitiatedBy role) {
        if (party.getAccountType() != AccountType.CUSTOMER) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_USER_TYPE_PREFIX + role + ErrorCodes.USER_TYPE_SUFFIX,
                    role + " user type " + party.getAccountType() + " not allowed"
            );
        }
    }

    private AccountIdentifier validateIdentifierMapping(Party party) {

        Identifier identifier = party.getIdentifier();
        Optional<AccountIdentifier> identifiers =
                accountIdentifierRepository
                        .findByIdentifierTypeAndIdentifierValueAndStatus(
                                identifier.getType().name(),
                                identifier.getValue(),
                                Constants.ACCOUNT_STATUS_ACTIVE
                        );
        if (identifiers.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.ACCOUNT_IDENTIFIER_NOT_FOUND,
                    "No active account identifier found for identifier value: " + identifier.getValue()
            );
        }
/*
        if (identifiers.size() > 1) {
            throw new ApplicationException(
                    "VALID_ACCOUNT_IDENTIFIER_NOT_FOUND",
                    "Multiple active account identifiers found for identifier value: "+ identifier.getValue()
            );
        }

 */

        return identifiers.get();
    }

    private Account identifyAccountFromAccountId(AccountIdentifier accountIdentifier) {

        List<Account> account = accountRepository
                .findByAccountIdAndStatus(
                        accountIdentifier.getAccountId(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                );

        if (account.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.ACCOUNT_NOT_FOUND,
                    "No active account found for identifier value: " + accountIdentifier.getIdentifierValue()
            );
        }

        if (account.size() > 1) {
            throw new ApplicationException(
                    ErrorCodes.VALID_ACCOUNT_NOT_FOUND,
                    "Multiple active account found for identifier value: "+ accountIdentifier.getIdentifierValue()
            );
        }
            return account.get(0);
    }

    private AccountAuth getAuthorizationRecord(AccountIdentifier accountIdentifier) {

        List<AccountAuth> accountAuth = accountAuthRepository
                .findByIdAndStatus(
                        accountIdentifier.getAuthId(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                );
        if (accountAuth.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.ACCOUNT_AUTH_NOT_FOUND,
                    "No active account Authorization found for identifier value: " + accountIdentifier.getIdentifierValue()
            );
        }

        if (accountAuth.size() > 1) {
            throw new ApplicationException(
                    ErrorCodes.VALID_ACCOUNT_AUTH_NOT_FOUND,
                    "Multiple active account Authorization found for identifier value: "+ accountIdentifier.getIdentifierValue()
            );
        }

        return accountAuth.get(0);
    }

    private Wallet getDefaultWallet(String accountId, String role) {

        List<Wallet> wallets = walletRepository
                .findByAccountIdAndIsDefault(accountId, Boolean.TRUE);

        if (wallets.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.WALLET_NOT_FOUND,
                    role + " default wallet not found"
            );
        }

        if (wallets.size() > 1) {
            throw new ApplicationException(
                    ErrorCodes.MULTIPLE_DEFAULT_WALLETS,
                    "Multiple default wallets found for " + role + " account"
            );
        }

        Wallet wallet = wallets.get(0);

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_WALLET,
                    role + " wallet is not active"
            );
        }

        return wallet;
    }
}

