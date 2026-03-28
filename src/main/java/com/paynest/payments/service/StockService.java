package com.paynest.payments.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.enums.AccountType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.StockApprovalRequest;
import com.paynest.payments.dto.StockInitiateRequest;
import com.paynest.payments.dto.StockReimbursementInitiateRequest;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.payments.service.BalanceService;
import com.paynest.payments.service.TransactionsService;
import com.paynest.users.service.WalletCacheService;
import com.paynest.config.tenant.TraceContext;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.paynest.config.security.JWTUtils.getCurrentAccountId;
import static com.paynest.config.security.JWTUtils.getCurrentAccountType;

@Service
@Transactional
@RequiredArgsConstructor
public class StockService {

    private static final String BANK_WALLET_TYPE = "BANK";
    private static final String MAIN_WALLET_TYPE = "MAIN";
    private static final String DEFAULT_ACCOUNT_ID = "SYS0001";
    private static final String STOCK_REIMBURSEMENT_OPERATION = "STOCK_REIMBURSEMENT";
    private static final String LEDGER_REF_TYPE_BALANCE = "WALLET_BALANCE";
    private static final String BALANCE_TYPE_AVAILABLE = "AVAILABLE";
    private static final String BALANCE_TYPE_FROZEN = "FROZEN";

    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;
    private final WalletCacheService walletCacheService;
    private final PropertyReader propertyReader;

    public BasePaymentResponse initiateStock(StockInitiateRequest request) {

        validateRequest(request);
        if(!getCurrentAccountType().equalsIgnoreCase("ADMIN")){
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        Account systemAccount = getActiveAccount(DEFAULT_ACCOUNT_ID);

        String currency = request.getTransaction().getCurrency();
        Wallet bankWallet = getWallet(systemAccount.getAccountId(), currency, BANK_WALLET_TYPE, "BANK");
        Wallet mainWallet = getWallet(systemAccount.getAccountId(), currency, MAIN_WALLET_TYPE, "MAIN");

        String transactionId = IdGenerator.generateTransactionId("ST");

        AccountIdentifier debtorIdentifier = buildAccountIdentifier(systemAccount.getAccountId());
        AccountIdentifier creditorIdentifier = buildAccountIdentifier(systemAccount.getAccountId());

        transactionsService.generateTransactionRecord(
                transactionId,
                request.getTransaction().getAmount(),
                "MOBILE",
                request.getOperationType(),
                debtorIdentifier,
                creditorIdentifier,
                bankWallet,
                mainWallet,
                InitiatedBy.DEBITOR
        );

        updateOptionalTransactionFields(transactionId, request);

        return BasePaymentResponse.builder()
                .responseStatus(TransactionStatus.PENDING)
                .operationType(request.getOperationType())
                .code("STOCK_INITIATED")
                .message("Stock transaction initiated")
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .currency(currency)
                .build();
    }

    public BasePaymentResponse initiateStockReimbursement(StockReimbursementInitiateRequest request) {

        validateReimbursementRequest(request);
        if (!getCurrentAccountType().equalsIgnoreCase("ADMIN")) {
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        validateParty(request.getDebitor(), "DEBITOR", AccountType.SUBSCRIBER);
        validateParty(request.getTransactor(), "TRANSACTOR", null);

        AccountIdentifier debtorIdentifier = validateIdentifierMapping(request.getDebitor());
        Account debtorAccount = getActiveAccount(debtorIdentifier.getAccountId());

        if (!debtorAccount.getAccountType().equalsIgnoreCase(request.getDebitor().getAccountType().name())) {
            throw new ApplicationException(ErrorCodes.INVALID_DEBITOR_ACCOUNT_TYPE, "Debitor account type mismatch");
        }

        Account systemAccount = getActiveAccount(DEFAULT_ACCOUNT_ID);
        String currency = request.getTransaction().getCurrency();

        Wallet debtorWallet = getWallet(debtorAccount.getAccountId(), currency, MAIN_WALLET_TYPE, "DEBTOR");
        Wallet creditorWallet = getWallet(systemAccount.getAccountId(), currency, MAIN_WALLET_TYPE, "CREDITOR");

        String transactionId = IdGenerator.generateTransactionId("SR");
        AccountIdentifier creditorIdentifier = buildAccountIdentifier(systemAccount.getAccountId());

        transactionsService.generateTransactionRecord(
                transactionId,
                request.getTransaction().getAmount(),
                "WEB",
                STOCK_REIMBURSEMENT_OPERATION,
                debtorIdentifier,
                creditorIdentifier,
                debtorWallet,
                creditorWallet,
                InitiatedBy.CREDITOR
        );

        updateOptionalTransactionFields(transactionId, request);
        freezeDebtorBalance(transactionId, debtorWallet, creditorWallet, request.getTransaction().getAmount());

        return BasePaymentResponse.builder()
                .responseStatus(TransactionStatus.PENDING)
                .operationType(STOCK_REIMBURSEMENT_OPERATION)
                .code("STOCK_REIMBURSEMENT_INITIATED")
                .message("Stock reimbursement initiated and pending approval")
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(request.getTransaction().getAmount())
                .currency(currency)
                .build();
    }

    public BasePaymentResponse updateStockTransactionStatus(StockApprovalRequest request) {

        if(!getCurrentAccountType().equalsIgnoreCase("ADMIN")){
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        validateApprovalRequest(request);

        Transactions transaction = transactionsRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TXN_NOT_FOUND, "Transaction not found"));

        if (!Constants.TRANSACTION_INITIATED.equalsIgnoreCase(transaction.getTransferStatus())
                && !Constants.TRANSACTION_PENDING.equalsIgnoreCase(transaction.getTransferStatus())) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_STATUS,
                    "Only initiated or pending stock transactions can be updated"
            );
        }

        List<TransactionDetails> transactionDetails = transactionDetailsRepository
                .findByIdTransactionId(request.getTransactionId());

        if (transactionDetails.size() != 2) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_DETAILS,
                    "Expected exactly two transaction details for stock transaction"
            );
        }

        TransactionDetails debitDetail = getTransactionDetail(transactionDetails, 1L);
        TransactionDetails creditDetail = getTransactionDetail(transactionDetails, 2L);

        Wallet debtorWallet = getWalletById(debitDetail.getWalletNumber(), "DEBITOR");
        Wallet creditorWallet = getWalletById(creditDetail.getWalletNumber(), "CREDITOR");
        boolean reimbursementTransaction = STOCK_REIMBURSEMENT_OPERATION.equalsIgnoreCase(transaction.getServiceCode());

        if ("APPROVED".equalsIgnoreCase(request.getStatus())
                || "SUCCESS".equalsIgnoreCase(request.getStatus())) {

            BigDecimal amount = toDisplayAmount(transaction.getTransactionValue());
            if (reimbursementTransaction) {
                settleFrozenReimbursement(transaction.getTransactionId(), debtorWallet, creditorWallet, transaction.getTransactionValue());
            } else {
                balanceService.transferWalletAmount(
                        debtorWallet,
                        creditorWallet,
                        amount,
                        transaction.getServiceCode(),
                        transaction.getTransactionId()
                );
            }
            transactionsService.updateApproveOrRejectComments(transaction.getTransactionId(), request.getComments());

            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.SUCCESS)
                    .operationType(transaction.getServiceCode())
                    .code("STOCK_APPROVED")
                    .message("Stock transaction approved successfully")
                    .timestamp(Instant.now())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transaction.getTransactionId())
                    .amount(amount)
                    .currency(creditorWallet.getCurrency())
                    .build();
        }

        if ("FAILED".equalsIgnoreCase(request.getStatus())
                || "REJECTED".equalsIgnoreCase(request.getStatus())) {

            String errorCode = request.getErrorCode() == null || request.getErrorCode().isBlank()
                    ? ErrorCodes.STOCK_REJECTED
                    : request.getErrorCode();

            transactionsService.updateApproveOrRejectComments(transaction.getTransactionId(), request.getComments());
            if (reimbursementTransaction) {
                releaseFrozenReimbursement(
                        transaction.getTransactionId(),
                        debtorWallet,
                        creditorWallet,
                        transaction.getTransactionValue(),
                        errorCode
                );
            } else {
                transactionsService.updateFailedTransactionRecord(
                        transaction.getTransactionId(),
                        errorCode,
                        getCurrentAccountId()
                );
            }

            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.FAILURE)
                    .operationType(transaction.getServiceCode())
                    .code(errorCode)
                    .message("Stock transaction marked as failed")
                    .timestamp(Instant.now())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transaction.getTransactionId())
                    .amount(toDisplayAmount(transaction.getTransactionValue()))
                    .currency(creditorWallet.getCurrency())
                    .build();
        }

        throw new ApplicationException(
                ErrorCodes.INVALID_STATUS,
                "Supported status values are APPROVED, SUCCESS, FAILED, or REJECTED"
        );
    }

    public BasePaymentResponse updateStockReimbursementTransactionStatus(StockApprovalRequest request) {

        if (!getCurrentAccountType().equalsIgnoreCase("ADMIN")) {
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        validateApprovalRequest(request);

        Transactions transaction = transactionsRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TXN_NOT_FOUND, "Transaction not found"));

        if (!STOCK_REIMBURSEMENT_OPERATION.equalsIgnoreCase(transaction.getServiceCode())) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_TYPE,
                    "Transaction is not a stock reimbursement transaction"
            );
        }

        if (!Constants.TRANSACTION_PENDING.equalsIgnoreCase(transaction.getTransferStatus())) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_STATUS,
                    "Only pending stock reimbursement transactions can be updated"
            );
        }

        List<TransactionDetails> transactionDetails = transactionDetailsRepository
                .findByIdTransactionId(request.getTransactionId());

        if (transactionDetails.size() != 2) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_TRANSACTION_DETAILS,
                    "Expected exactly two transaction details for stock reimbursement transaction"
            );
        }

        TransactionDetails debitDetail = getTransactionDetail(transactionDetails, 1L);
        TransactionDetails creditDetail = getTransactionDetail(transactionDetails, 2L);

        Wallet debtorWallet = getWalletById(debitDetail.getWalletNumber(), "DEBITOR");
        Wallet creditorWallet = getWalletById(creditDetail.getWalletNumber(), "CREDITOR");

        if ("APPROVED".equalsIgnoreCase(request.getStatus())
                || "SUCCESS".equalsIgnoreCase(request.getStatus())) {

            approveStockReimbursement(
                    transaction.getTransactionId(),
                    debtorWallet,
                    creditorWallet,
                    transaction.getTransactionValue()
            );
            transactionsService.updateApproveOrRejectComments(transaction.getTransactionId(), request.getComments());

            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.SUCCESS)
                    .operationType(transaction.getServiceCode())
                    .code("STOCK_REIMBURSEMENT_APPROVED")
                    .message("Stock reimbursement transaction approved successfully")
                    .timestamp(Instant.now())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transaction.getTransactionId())
                    .amount(toDisplayAmount(transaction.getTransactionValue()))
                    .currency(creditorWallet.getCurrency())
                    .build();
        }

        if ("FAILED".equalsIgnoreCase(request.getStatus())
                || "REJECTED".equalsIgnoreCase(request.getStatus())) {

            String errorCode = request.getErrorCode() == null || request.getErrorCode().isBlank()
                    ? ErrorCodes.STOCK_REIMBURSEMENT_REJECTED
                    : request.getErrorCode();

            rejectStockReimbursement(
                    transaction.getTransactionId(),
                    debtorWallet,
                    creditorWallet,
                    transaction.getTransactionValue(),
                    errorCode
            );
            transactionsService.updateApproveOrRejectComments(transaction.getTransactionId(), request.getComments());

            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.FAILURE)
                    .operationType(transaction.getServiceCode())
                    .code(errorCode)
                    .message("Stock reimbursement transaction rejected")
                    .timestamp(Instant.now())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transaction.getTransactionId())
                    .amount(toDisplayAmount(transaction.getTransactionValue()))
                    .currency(creditorWallet.getCurrency())
                    .build();
        }

        throw new ApplicationException(
                ErrorCodes.INVALID_STATUS,
                "Supported status values are APPROVED, SUCCESS, FAILED, or REJECTED"
        );
    }

    private void validateRequest(StockInitiateRequest request) {
        if (request == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Request body cannot be null");
        }
        if (request.getTransaction() == null) {
            throw new ApplicationException(ErrorCodes.TRANSACTION_MISSING, "Transaction details required");
        }
        if (request.getTransaction().getAmount() == null
                || request.getTransaction().getAmount().signum() <= 0) {
            throw new ApplicationException(ErrorCodes.INVALID_AMOUNT, "Transaction amount must be greater than zero");
        }
        if (request.getTransaction().getCurrency() == null
                || request.getTransaction().getCurrency().isBlank()) {
            throw new ApplicationException(ErrorCodes.CURRENCY_MISSING, "Currency is required");
        }
        if (request.getOperationType() == null || request.getOperationType().isBlank()) {
            throw new ApplicationException(ErrorCodes.OPERATION_TYPE_MISSING, "Operation type is required");
        }
    }

    private void validateReimbursementRequest(StockReimbursementInitiateRequest request) {
        if (request == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Request body cannot be null");
        }
        if (request.getDebitor() == null) {
            throw new ApplicationException(ErrorCodes.DEBITOR_MISSING, "Debitor is required");
        }
        if (request.getTransactor() == null) {
            throw new ApplicationException(ErrorCodes.TRANSACTOR_MISSING, "Transactor is required");
        }
        if (request.getTransaction() == null) {
            throw new ApplicationException(ErrorCodes.TRANSACTION_MISSING, "Transaction details required");
        }
        if (request.getTransaction().getAmount() == null
                || request.getTransaction().getAmount().signum() <= 0) {
            throw new ApplicationException(ErrorCodes.INVALID_AMOUNT, "Transaction amount must be greater than zero");
        }
        if (request.getTransaction().getCurrency() == null
                || request.getTransaction().getCurrency().isBlank()) {
            throw new ApplicationException(ErrorCodes.CURRENCY_MISSING, "Currency is required");
        }
        validateIdentifier(request.getDebitor(), "DEBITOR");
        validateIdentifier(request.getTransactor(), "TRANSACTOR");
    }

    private void validateApprovalRequest(StockApprovalRequest request) {
        if (request == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Request body cannot be null");
        }
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            throw new ApplicationException(ErrorCodes.TXN_ID_MISSING, "Transaction id is required");
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new ApplicationException(ErrorCodes.STATUS_MISSING, "Status is required");
        }
    }

    private Account getActiveAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.ACCOUNT_NOT_FOUND," account not found"));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT," account is not active");
        }

        return account;
    }

    private void validateParty(Party party, String role, AccountType expectedAccountType) {
        if (party.getAccountType() == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_ACCOUNT_TYPE_PREFIX + role + ErrorCodes.ACCOUNT_TYPE_SUFFIX,
                    role + " account type is required"
            );
        }

        if (expectedAccountType != null && party.getAccountType() != expectedAccountType) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_ACCOUNT_TYPE_PREFIX + role + ErrorCodes.ACCOUNT_TYPE_SUFFIX,
                    role + " account type " + party.getAccountType() + " not allowed"
            );
        }
    }

    private void validateIdentifier(Party party, String role) {
        Identifier identifier = party.getIdentifier();

        if (identifier == null || identifier.getType() == null || identifier.getValue() == null
                || identifier.getValue().isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_USER_TYPE_PREFIX + role + "_IDENTIFIER",
                    role + " identifier is required"
            );
        }
    }

    private AccountIdentifier validateIdentifierMapping(Party party) {
        Identifier identifier = party.getIdentifier();

        Optional<AccountIdentifier> accountIdentifier = accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        identifier.getType().name(),
                        identifier.getValue(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                );

        return accountIdentifier.orElseThrow(() -> new ApplicationException(
                ErrorCodes.ACCOUNT_IDENTIFIER_NOT_FOUND,
                "No active account identifier found for identifier value: " + identifier.getValue()
        ));
    }

    private Wallet getWallet(String accountId, String currency, String walletType, String role) {
        Wallet wallet = walletRepository.findByAccountIdAndCurrencyAndWalletType(accountId, currency, walletType)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.WALLET_NOT_FOUND,
                        role + " wallet not found for currency " + currency));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, role + " wallet is not active");
        }

        return wallet;
    }

    private Wallet getWalletById(String walletId, String role) {
        Long parsedWalletId;
        try {
            parsedWalletId = Long.parseLong(walletId);
        } catch (NumberFormatException ex) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, role + " wallet number is invalid");
        }

        Wallet wallet = walletRepository.findById(parsedWalletId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.WALLET_NOT_FOUND, role + " wallet not found"));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_WALLET, role + " wallet is not active");
        }

        return wallet;
    }

    private TransactionDetails getTransactionDetail(List<TransactionDetails> details, Long sequenceNumber) {
        return details.stream()
                .filter(detail -> sequenceNumber.equals(detail.getId().getTxnSequenceNumber()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.TRANSACTION_DETAIL_NOT_FOUND,
                        "Transaction detail not found for sequence " + sequenceNumber
                ));
    }

    private BigDecimal toDisplayAmount(BigDecimal storedAmount) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        return storedAmount.divide(currencyFactor, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toStoredAmount(BigDecimal displayAmount) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        return displayAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);
    }

    private AccountIdentifier buildAccountIdentifier(String accountId) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(IdentifierType.ACCOUNT_ID.name());
        identifier.setIdentifierValue(accountId);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private void updateOptionalTransactionFields(String transactionId, StockInitiateRequest request) {
        transactionsService.updateMetadata(transactionId, toJson(request.getMetadata()));
        transactionsService.updateAdditionalInfo(transactionId, toJson(request.getAdditionalInfo()));
        transactionsService.updatePaymentReference(transactionId, request.getPaymentReference());
        transactionsService.updateComments(transactionId, request.getComments());
    }

    private void updateOptionalTransactionFields(String transactionId, StockReimbursementInitiateRequest request) {
        transactionsService.updateMetadata(transactionId, toJson(request.getMetadata()));
        transactionsService.updateAdditionalInfo(transactionId, toJson(request.getAdditionalInfo()));
        transactionsService.updatePaymentReference(transactionId, request.getPaymentReference());
        transactionsService.updateComments(transactionId, request.getComments());
    }

    private void freezeDebtorBalance(
            String transactionId,
            Wallet debtorWallet,
            Wallet creditorWallet,
            BigDecimal amount) {

        BigDecimal storedAmount = toStoredAmount(amount);

        WalletBalance debtorBalance = walletBalanceRepository.lockBalance(debtorWallet.getWalletId());
        WalletBalance creditorBalance = walletBalanceRepository.lockBalance(creditorWallet.getWalletId());

        BigDecimal debtorAvailableBefore = debtorBalance.getAvailableBalance();
        BigDecimal debtorFicBefore = debtorBalance.getFicBalance();
        BigDecimal debtorFrozenBefore = debtorBalance.getFrozenBalance();
        BigDecimal debtorNetBalance = debtorAvailableBefore
                .subtract(debtorFicBefore)
                .subtract(debtorFrozenBefore);

        if (debtorNetBalance.compareTo(storedAmount) < 0) {
            transactionsService.updateFailedTransactionRecord(
                    transactionId,
                    ErrorCodes.INSUFFICIENT_BALANCE,
                    debtorWallet.getAccountId()
            );
            throw new ApplicationException(ErrorCodes.INSUFFICIENT_BALANCE, "Insufficient balance");
        }

        BigDecimal creditorAvailableBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
        BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();

        debtorBalance.setFrozenBalance(debtorFrozenBefore.add(storedAmount));
        saveWalletLedger(
                transactionId,
                debtorWallet,
                Constants.TXN_TYPE_DR,
                storedAmount,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_FROZEN,
                "Stock reimbursement amount frozen"
        );
        walletBalanceRepository.save(debtorBalance);

        transactionsRepository.updateStatus(transactionId, Constants.TRANSACTION_PENDING, null);

        transactionDetailsRepository.updateBalances(
                transactionId,
                1L,
                debtorAvailableBefore,
                debtorBalance.getAvailableBalance(),
                debtorFicBefore,
                debtorFicBefore,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                Constants.TRANSACTION_PENDING
        );

        transactionDetailsRepository.updateBalances(
                transactionId,
                2L,
                creditorAvailableBefore,
                creditorAvailableBefore,
                creditorFicBefore,
                creditorFicBefore,
                creditorFrozenBefore,
                creditorFrozenBefore,
                Constants.TRANSACTION_PENDING
        );

        walletCacheService.refreshAccountWallets(debtorWallet.getAccountId());
        walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
    }

    private void settleFrozenReimbursement(
            String transactionId,
            Wallet debtorWallet,
            Wallet creditorWallet,
            BigDecimal storedAmount) {

        WalletBalance debtorBalance = walletBalanceRepository.lockBalance(debtorWallet.getWalletId());
        WalletBalance creditorBalance = walletBalanceRepository.lockBalance(creditorWallet.getWalletId());

        BigDecimal debtorAvailableBefore = debtorBalance.getAvailableBalance();
        BigDecimal debtorFicBefore = debtorBalance.getFicBalance();
        BigDecimal debtorFrozenBefore = debtorBalance.getFrozenBalance();

        if (debtorFrozenBefore.compareTo(storedAmount) < 0) {
            throw new ApplicationException(ErrorCodes.INSUFFICIENT_FROZEN_BALANCE, "Insufficient frozen balance");
        }

        BigDecimal creditorAvailableBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
        BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();

        debtorBalance.setFrozenBalance(debtorFrozenBefore.subtract(storedAmount));
        creditorBalance.setAvailableBalance(creditorAvailableBefore.add(storedAmount));

        saveWalletLedger(
                transactionId,
                debtorWallet,
                Constants.TXN_TYPE_CR,
                storedAmount,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_FROZEN,
                "Stock reimbursement amount released from frozen balance"
        );
        saveWalletLedger(
                transactionId,
                creditorWallet,
                Constants.TXN_TYPE_CR,
                storedAmount,
                creditorAvailableBefore,
                creditorBalance.getAvailableBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_AVAILABLE,
                "Stock reimbursement amount credited"
        );

        walletBalanceRepository.save(debtorBalance);
        walletBalanceRepository.save(creditorBalance);

        transactionsRepository.updateStatus(transactionId, Constants.TRANSACTION_SUCCESS, null);

        transactionDetailsRepository.updateBalances(
                transactionId,
                1L,
                debtorAvailableBefore,
                debtorAvailableBefore,
                debtorFicBefore,
                debtorFicBefore,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                Constants.TRANSACTION_SUCCESS
        );

        transactionDetailsRepository.updateBalances(
                transactionId,
                2L,
                creditorAvailableBefore,
                creditorBalance.getAvailableBalance(),
                creditorFicBefore,
                creditorFicBefore,
                creditorFrozenBefore,
                creditorFrozenBefore,
                Constants.TRANSACTION_SUCCESS
        );

        walletCacheService.refreshAccountWallets(debtorWallet.getAccountId());
        walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
    }

    private void releaseFrozenReimbursement(
            String transactionId,
            Wallet debtorWallet,
            Wallet creditorWallet,
            BigDecimal storedAmount,
            String errorCode) {

        WalletBalance debtorBalance = walletBalanceRepository.lockBalance(debtorWallet.getWalletId());
        WalletBalance creditorBalance = walletBalanceRepository.lockBalance(creditorWallet.getWalletId());

        BigDecimal debtorAvailableBefore = debtorBalance.getAvailableBalance();
        BigDecimal debtorFicBefore = debtorBalance.getFicBalance();
        BigDecimal debtorFrozenBefore = debtorBalance.getFrozenBalance();

        if (debtorFrozenBefore.compareTo(storedAmount) < 0) {
            throw new ApplicationException(ErrorCodes.INSUFFICIENT_FROZEN_BALANCE, "Insufficient frozen balance");
        }

        BigDecimal creditorAvailableBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
        BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();

        debtorBalance.setFrozenBalance(debtorFrozenBefore.subtract(storedAmount));

        saveWalletLedger(
                transactionId,
                debtorWallet,
                Constants.TXN_TYPE_CR,
                storedAmount,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_FROZEN,
                "Stock reimbursement frozen amount released"
        );

        walletBalanceRepository.save(debtorBalance);

        transactionsRepository.updateStatus(transactionId, Constants.TRANSACTION_FAILED, errorCode);

        transactionDetailsRepository.updateBalances(
                transactionId,
                1L,
                debtorAvailableBefore,
                debtorAvailableBefore,
                debtorFicBefore,
                debtorFicBefore,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                Constants.TRANSACTION_FAILED
        );

        transactionDetailsRepository.updateBalances(
                transactionId,
                2L,
                creditorAvailableBefore,
                creditorAvailableBefore,
                creditorFicBefore,
                creditorFicBefore,
                creditorFrozenBefore,
                creditorFrozenBefore,
                Constants.TRANSACTION_FAILED
        );

        walletCacheService.refreshAccountWallets(debtorWallet.getAccountId());
        walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
    }

    private void approveStockReimbursement(
            String transactionId,
            Wallet debtorWallet,
            Wallet creditorWallet,
            BigDecimal storedAmount) {

        WalletBalance debtorBalance = walletBalanceRepository.lockBalance(debtorWallet.getWalletId());
        WalletBalance creditorBalance = walletBalanceRepository.lockBalance(creditorWallet.getWalletId());

        BigDecimal debtorAvailableBefore = debtorBalance.getAvailableBalance();
        BigDecimal debtorFicBefore = debtorBalance.getFicBalance();
        BigDecimal debtorFrozenBefore = debtorBalance.getFrozenBalance();

        if (debtorFrozenBefore.compareTo(storedAmount) < 0) {
            throw new ApplicationException(ErrorCodes.INSUFFICIENT_FROZEN_BALANCE, "Insufficient frozen balance");
        }
        if (debtorAvailableBefore.compareTo(storedAmount) < 0) {
            throw new ApplicationException(ErrorCodes.INSUFFICIENT_BALANCE, "Insufficient balance");
        }

        BigDecimal creditorAvailableBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
        BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();

        debtorBalance.setFrozenBalance(debtorFrozenBefore.subtract(storedAmount));
        debtorBalance.setAvailableBalance(debtorAvailableBefore.subtract(storedAmount));
        creditorBalance.setAvailableBalance(creditorAvailableBefore.add(storedAmount));

        saveWalletLedger(
                transactionId,
                debtorWallet,
                Constants.TXN_TYPE_CR,
                storedAmount,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_FROZEN,
                "Stock reimbursement frozen amount consumed"
        );
        saveWalletLedger(
                transactionId,
                debtorWallet,
                Constants.TXN_TYPE_DR,
                storedAmount,
                debtorAvailableBefore,
                debtorBalance.getAvailableBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_AVAILABLE,
                "Stock reimbursement amount debited"
        );
        saveWalletLedger(
                transactionId,
                creditorWallet,
                Constants.TXN_TYPE_CR,
                storedAmount,
                creditorAvailableBefore,
                creditorBalance.getAvailableBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_AVAILABLE,
                "Stock reimbursement amount credited"
        );

        walletBalanceRepository.save(debtorBalance);
        walletBalanceRepository.save(creditorBalance);

        transactionsRepository.updateStatus(transactionId, Constants.TRANSACTION_SUCCESS, null);

        transactionDetailsRepository.updateBalances(
                transactionId,
                1L,
                debtorAvailableBefore,
                debtorBalance.getAvailableBalance(),
                debtorFicBefore,
                debtorFicBefore,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                Constants.TRANSACTION_SUCCESS
        );

        transactionDetailsRepository.updateBalances(
                transactionId,
                2L,
                creditorAvailableBefore,
                creditorBalance.getAvailableBalance(),
                creditorFicBefore,
                creditorFicBefore,
                creditorFrozenBefore,
                creditorFrozenBefore,
                Constants.TRANSACTION_SUCCESS
        );

        walletCacheService.refreshAccountWallets(debtorWallet.getAccountId());
        walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
    }

    private void rejectStockReimbursement(
            String transactionId,
            Wallet debtorWallet,
            Wallet creditorWallet,
            BigDecimal storedAmount,
            String errorCode) {

        WalletBalance debtorBalance = walletBalanceRepository.lockBalance(debtorWallet.getWalletId());
        WalletBalance creditorBalance = walletBalanceRepository.lockBalance(creditorWallet.getWalletId());

        BigDecimal debtorAvailableBefore = debtorBalance.getAvailableBalance();
        BigDecimal debtorFicBefore = debtorBalance.getFicBalance();
        BigDecimal debtorFrozenBefore = debtorBalance.getFrozenBalance();

        if (debtorFrozenBefore.compareTo(storedAmount) < 0) {
            throw new ApplicationException(ErrorCodes.INSUFFICIENT_FROZEN_BALANCE, "Insufficient frozen balance");
        }

        BigDecimal creditorAvailableBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
        BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();

        debtorBalance.setFrozenBalance(debtorFrozenBefore.subtract(storedAmount));

        saveWalletLedger(
                transactionId,
                debtorWallet,
                Constants.TXN_TYPE_CR,
                storedAmount,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                STOCK_REIMBURSEMENT_OPERATION,
                BALANCE_TYPE_FROZEN,
                "Stock reimbursement frozen amount released on rejection"
        );

        walletBalanceRepository.save(debtorBalance);
        transactionsRepository.updateStatus(transactionId, Constants.TRANSACTION_FAILED, errorCode);

        transactionDetailsRepository.updateBalances(
                transactionId,
                1L,
                debtorAvailableBefore,
                debtorAvailableBefore,
                debtorFicBefore,
                debtorFicBefore,
                debtorFrozenBefore,
                debtorBalance.getFrozenBalance(),
                Constants.TRANSACTION_FAILED
        );

        transactionDetailsRepository.updateBalances(
                transactionId,
                2L,
                creditorAvailableBefore,
                creditorAvailableBefore,
                creditorFicBefore,
                creditorFicBefore,
                creditorFrozenBefore,
                creditorFrozenBefore,
                Constants.TRANSACTION_FAILED
        );

        walletCacheService.refreshAccountWallets(debtorWallet.getAccountId());
        walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
    }

    private JSONObject toJson(Map<String, Object> value) {
        return value == null ? new JSONObject() : new JSONObject(value);
    }

    private void saveWalletLedger(
            String transactionId,
            Wallet wallet,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String txnType,
            String balanceType,
            String description) {

        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(transactionId);
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setCurrency(wallet.getCurrency());
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setTxnType(txnType);
        ledger.setReferenceType(LEDGER_REF_TYPE_BALANCE);
        ledger.setReferenceId(TraceContext.getTraceId());
        ledger.setDescription(description);
        ledger.setAttr1(balanceType);

        walletLedgerRepository.save(ledger);
    }
}

