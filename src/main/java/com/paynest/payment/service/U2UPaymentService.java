package com.paynest.payment.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.enums.*;
import com.paynest.payment.dto.Identifier;
import com.paynest.payment.dto.Party;
import com.paynest.payment.dto.U2UPaymentRequest;
import com.paynest.payment.dto.U2UPaymentResponse;
import com.paynest.entity.*;
import com.paynest.exception.ApplicationException;
import com.paynest.payment.validation.BasePaymentRequestValidator;
import com.paynest.repository.*;
import com.paynest.service.BalanceService;
import com.paynest.service.TransactionsService;
import com.paynest.tenant.TraceContext;
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

    public U2UPaymentResponse processPayment(U2UPaymentRequest request) {

        log.info("Processing U2U payment request. traceId={}", TraceContext.getTraceId());

        basePaymentRequestValidator.validate(request);

        validateParty(request.getDebitor(), InitiatedBy.DEBITOR);
        validateParty(request.getCreditor(), InitiatedBy.CREDITOR);

        AccountIdentifier debtorIdentifier =
                validateIdentifierMapping(request.getDebitor());

        AccountIdentifier creditorIdentifier =
                validateIdentifierMapping(request.getCreditor());

        Account debtorAccount =
                identifyAccountFromAccountId(debtorIdentifier);

        Account creditorAccount =
                identifyAccountFromAccountId(creditorIdentifier);

        if (!debtorAccount.getAccountType()
                .equalsIgnoreCase(request.getDebitor().getAccountType().name())) {
            throw new ApplicationException(
                    "INVALID_DEBTOR_ACCOUNT_TYPE",
                    "Debtor account type mismatch"
            );
        }

        if (!creditorAccount.getAccountType()
                .equalsIgnoreCase(request.getCreditor().getAccountType().name())) {
            throw new ApplicationException(
                    "INVALID_CREDITOR_ACCOUNT_TYPE",
                    "Creditor account type mismatch"
            );
        }

        if(InitiatedBy.CREDITOR == request.getInitiatedBy()){
            //
        } else if (InitiatedBy.DEBITOR == request.getInitiatedBy()) {
            AccountAuth debitorAccountAuth = getAuthorizationRecord(debtorIdentifier);
            boolean isAuthSuccessful = IdGenerator.verifyPin(
                    request.getDebitor().getAuthentication().getValue(),
                    debitorAccountAuth.getAuthHash(),
                    debitorAccountAuth.getAuthValue()
            );
            if (false) {

                int attempts = debitorAccountAuth.getFailedAttempts() == null
                        ? 1
                        : debitorAccountAuth.getFailedAttempts() + 1;

                debitorAccountAuth.setFailedAttempts(attempts);
                debitorAccountAuth.setLastFailedAt(LocalDateTime.now());

                int maxInvalidAuthAttempts = Integer.parseInt(
                        propertyReader.getPropertyValue("max.allowed.invalid.auth.attempts"));//TODO in DB

                if (attempts >= maxInvalidAuthAttempts) {

                    debitorAccountAuth.setStatus("LOCKED");
                    debtorAccount.setStatus("LOCKED");

                    accountAuthRepository.save(debitorAccountAuth);
                    accountRepository.save(debtorAccount);

                    throw new ApplicationException(
                            "INVALID_PIN",
                            "Maximum invalid PIN attempts reached. Wallet locked."
                    );
                }

                accountAuthRepository.save(debitorAccountAuth);

                throw new ApplicationException(
                        "INVALID_PIN",
                        "Invalid transaction PIN"
                );
            }
        }else{
            throw new ApplicationException(
                    "INVALID_INITIATOR",
                    "Initiator " + request.getInitiatedBy() + " not allowed"
            );
        }

        String transactionId = IdGenerator.generateTransactionId("UU");
        Wallet debtorWallet = getDefaultWallet(debtorAccount.getAccountId(), "DEBTOR");
        Wallet creditorWallet = getDefaultWallet(creditorAccount.getAccountId(), "CREDITOR"); //Todo

        transactionsService.generateTransactionRecord(transactionId,request.getTransaction().getAmount(),
                "MOBILE",request.getOperationType(),
                debtorIdentifier,creditorIdentifier,debtorWallet,creditorWallet,request.getInitiatedBy());

        JSONObject metaDataJson = new JSONObject(request.getMetadata());
        JSONObject additionalInfoJson = new JSONObject(request.getAdditionalInfo());

        transactionsService.updateMetadata(transactionId, metaDataJson);

        transactionsService.updateAdditionalInfo(transactionId, additionalInfoJson);

        transactionsService.updatePaymentReference(transactionId, request.getPaymentReference());

        transactionsService.updateComments(transactionId, request.getComments());

        balanceService.transferWalletAmount(debtorWallet,creditorWallet,request.getTransaction().getAmount(), request.getOperationType(), transactionId);

        return U2UPaymentResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(request.getOperationType().toString())
                .code("PAYMENT_SUCCESS")
                .message("U2U Payment Successful")
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .currency(request.getTransaction().getCurrency())
                .build();
    }

    private void validateParty(Party party, InitiatedBy role) {
        if (party.getAccountType() != AccountType.CUSTOMER) {
            throw new ApplicationException(
                    "INVALID_" + role + "_USER_TYPE",
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
                    "ACCOUNT_IDENTIFIER_NOT_FOUND",
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
                    "ACCOUNT_NOT_FOUND",
                    "No active account found for identifier value: " + accountIdentifier.getIdentifierValue()
            );
        }

        if (account.size() > 1) {
            throw new ApplicationException(
                    "VALID_ACCOUNT_NOT_FOUND",
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
                    "ACCOUNT_AUTH_NOT_FOUND",
                    "No active account Authorization found for identifier value: " + accountIdentifier.getIdentifierValue()
            );
        }

        if (accountAuth.size() > 1) {
            throw new ApplicationException(
                    "VALID_ACCOUNT_AUTH_NOT_FOUND",
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
                    "WALLET_NOT_FOUND",
                    role + " default wallet not found"
            );
        }

        if (wallets.size() > 1) {
            throw new ApplicationException(
                    "MULTIPLE_DEFAULT_WALLETS",
                    "Multiple default wallets found for " + role + " account"
            );
        }

        Wallet wallet = wallets.get(0);

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(
                    "INVALID_WALLET",
                    role + " wallet is not active"
            );
        }

        return wallet;
    }
}