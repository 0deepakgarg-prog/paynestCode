package com.paynest.payment.service;

import com.paynest.common.Constants;
import com.paynest.entity.TransactionDetails;
import com.paynest.entity.Transactions;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.entity.WalletLedger;
import com.paynest.enums.TransactionStatus;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payment.dto.SettleTransactionRequest;
import com.paynest.payment.dto.SettleTransactionResponse;
import com.paynest.repository.TransactionDetailsRepository;
import com.paynest.repository.TransactionsRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletLedgerRepository;
import com.paynest.repository.WalletRepository;
import com.paynest.security.JWTUtils;
import com.paynest.service.TransactionsService;
import com.paynest.tenant.TraceContext;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class TransactionSettlementService {

    private static final String OPERATION_NAME = "SETTLETXN";

    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final TransactionsService transactionsService;

    public TransactionSettlementService(
            TransactionsRepository transactionsRepository,
            TransactionDetailsRepository transactionDetailsRepository,
            WalletRepository walletRepository,
            WalletBalanceRepository walletBalanceRepository,
            WalletLedgerRepository walletLedgerRepository,
            TransactionsService transactionsService
    ) {
        this.transactionsRepository = transactionsRepository;
        this.transactionDetailsRepository = transactionDetailsRepository;
        this.walletRepository = walletRepository;
        this.walletBalanceRepository = walletBalanceRepository;
        this.walletLedgerRepository = walletLedgerRepository;
        this.transactionsService = transactionsService;
    }

    public SettleTransactionResponse settleTransaction(SettleTransactionRequest request) {
        validateRequest(request);

        Transactions transaction = transactionsRepository.findFirstByTraceId(request.getTraceId())
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.TRANSACTION_TRACE_NOT_FOUND,
                        null,
                        Map.of("traceId", request.getTraceId())
                ));

        if (!Constants.TRANSACTION_AMBIGUOUS.equalsIgnoreCase(transaction.getTransferStatus())) {
            throw new ApplicationException(
                    PaymentErrorCode.TRANSACTION_NOT_PENDING_SETTLEMENT,
                    null,
                    Map.of(
                            "traceId", request.getTraceId(),
                            "transactionId", transaction.getTransactionId(),
                            "currentStatus", transaction.getTransferStatus()
                    )
            );
        }

        List<TransactionDetails> transactionDetails = transactionDetailsRepository
                .findByIdTransactionId(transaction.getTransactionId());
        TransactionDetails debitDetail = getRequiredDetail(transactionDetails, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = getRequiredDetail(transactionDetails, Constants.TXN_TYPE_CR);

        Wallet debitorWallet = getRequiredWallet(debitDetail);
        Wallet creditorWallet = getRequiredWallet(creditDetail);
        String modifiedBy = getCurrentActorAccountId();

        if (Boolean.TRUE.equals(request.getSettlementStatus())) {
            settleSuccess(transaction, transactionDetails, creditDetail, creditorWallet, modifiedBy);
        } else {
            rollbackSettlement(transaction, transactionDetails, debitDetail, creditDetail, debitorWallet, creditorWallet, modifiedBy);
        }

        applyOptionalUpdates(transaction.getTransactionId(), request);

        return buildResponse(transaction, request.getSettlementStatus());
    }

    private void validateRequest(SettleTransactionRequest request) {
        if (request == null || request.getTraceId() == null || request.getTraceId().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.TRACE_ID_MISSING);
        }

        if (request.getSettlementStatus() == null) {
            throw new ApplicationException(PaymentErrorCode.SETTLEMENT_STATUS_MISSING);
        }

        if (request.getComments() != null) {
            String normalized = request.getComments().trim();
            if (normalized.length() > 300) {
                throw new ApplicationException(PaymentErrorCode.COMMENTS_TOO_LONG);
            }
            request.setComments(normalized.isEmpty() ? null : normalized);
        }

        request.setTraceId(request.getTraceId().trim());
    }

    private TransactionDetails getRequiredDetail(List<TransactionDetails> details, String entryType) {
        return details.stream()
                .filter(detail -> entryType.equalsIgnoreCase(detail.getEntryType()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.INVALID_TRANSACTION_DETAILS,
                        null,
                        Map.of("entryType", entryType)
                ));
    }

    private Wallet getRequiredWallet(TransactionDetails transactionDetail) {
        try {
            Long walletId = Long.parseLong(transactionDetail.getWalletNumber());
            return walletRepository.findById(walletId)
                    .orElseThrow(() -> new ApplicationException(
                            PaymentErrorCode.INVALID_TRANSACTION_DETAILS,
                            null,
                            Map.of("walletId", transactionDetail.getWalletNumber())
                    ));
        } catch (NumberFormatException ex) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_TRANSACTION_DETAILS,
                    null,
                    Map.of("walletId", transactionDetail.getWalletNumber())
            );
        }
    }

    private void settleSuccess(
            Transactions transaction,
            List<TransactionDetails> transactionDetails,
            TransactionDetails creditDetail,
            Wallet creditorWallet,
            String modifiedBy
    ) {
        WalletBalance creditorBalance = lockBalance(creditorWallet.getWalletId());
        BigDecimal amount = transaction.getTransactionValue();
        if (creditorBalance.getFicBalance().compareTo(amount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_FIC_BALANCE,
                    null,
                    Map.of(
                            "walletId", creditorWallet.getWalletId(),
                            "requiredAmount", amount.toPlainString(),
                            "ficBalance", creditorBalance.getFicBalance().toPlainString()
                    )
            );
        }

        BigDecimal creditorFicAfter = creditorBalance.getFicBalance().subtract(amount);
        creditorBalance.setFicBalance(creditorFicAfter);
        walletBalanceRepository.save(creditorBalance);

        LocalDateTime now = LocalDateTime.now();
        transaction.setPreviousStatus(transaction.getTransferStatus());
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transaction.setTransferOn(now);
        transaction.setModifiedOn(now);
        transaction.setModifiedBy(modifiedBy);
        transactionsRepository.save(transaction);

        for (TransactionDetails detail : transactionDetails) {
            detail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
            detail.setTransferOn(now);
        }
        creditDetail.setPostFicBalance(creditorFicAfter);
        transactionDetailsRepository.saveAll(transactionDetails);
    }

    private void rollbackSettlement(
            Transactions transaction,
            List<TransactionDetails> transactionDetails,
            TransactionDetails debitDetail,
            TransactionDetails creditDetail,
            Wallet debitorWallet,
            Wallet creditorWallet,
            String modifiedBy
    ) {
        BigDecimal amount = transaction.getTransactionValue();
        boolean lockDebitorFirst = debitorWallet.getWalletId() <= creditorWallet.getWalletId();
        WalletBalance firstLockedBalance = lockBalance(lockDebitorFirst
                ? debitorWallet.getWalletId()
                : creditorWallet.getWalletId());
        WalletBalance secondLockedBalance = lockBalance(lockDebitorFirst
                ? creditorWallet.getWalletId()
                : debitorWallet.getWalletId());

        WalletBalance debitorBalance = lockDebitorFirst ? firstLockedBalance : secondLockedBalance;
        WalletBalance creditorBalance = lockDebitorFirst ? secondLockedBalance : firstLockedBalance;

        if (creditorBalance.getFicBalance().compareTo(amount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_FIC_BALANCE,
                    null,
                    Map.of(
                            "walletId", creditorWallet.getWalletId(),
                            "requiredAmount", amount.toPlainString(),
                            "ficBalance", creditorBalance.getFicBalance().toPlainString()
                    )
            );
        }

        if (creditorBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_CREDITOR_BALANCE,
                    null,
                    Map.of(
                            "walletId", creditorWallet.getWalletId(),
                            "requiredAmount", amount.toPlainString(),
                            "availableBalance", creditorBalance.getAvailableBalance().toPlainString()
                    )
            );
        }

        BigDecimal debitorBalanceBefore = debitorBalance.getAvailableBalance();
        BigDecimal creditorBalanceBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();

        BigDecimal debitorBalanceAfter = debitorBalanceBefore.add(amount);
        BigDecimal creditorBalanceAfter = creditorBalanceBefore.subtract(amount);
        BigDecimal creditorFicAfter = creditorFicBefore.subtract(amount);

        debitorBalance.setAvailableBalance(debitorBalanceAfter);
        creditorBalance.setAvailableBalance(creditorBalanceAfter);
        creditorBalance.setFicBalance(creditorFicAfter);

        walletBalanceRepository.save(debitorBalance);
        walletBalanceRepository.save(creditorBalance);

        createRollbackLedger(transaction, creditorWallet, Constants.TXN_TYPE_DR, amount, creditorBalanceBefore, creditorBalanceAfter);
        createRollbackLedger(transaction, debitorWallet, Constants.TXN_TYPE_CR, amount, debitorBalanceBefore, debitorBalanceAfter);

        LocalDateTime now = LocalDateTime.now();
        transaction.setPreviousStatus(transaction.getTransferStatus());
        transaction.setTransferStatus(Constants.TRANSACTION_FAILED);
        transaction.setTransferOn(now);
        transaction.setModifiedOn(now);
        transaction.setModifiedBy(modifiedBy);
        transactionsRepository.save(transaction);

        for (TransactionDetails detail : transactionDetails) {
            detail.setTransferStatus(Constants.TRANSACTION_FAILED);
            detail.setTransferOn(now);
        }
        debitDetail.setPostBalance(debitorBalanceAfter);
        creditDetail.setPostBalance(creditorBalanceAfter);
        creditDetail.setPostFicBalance(creditorFicAfter);
        transactionDetailsRepository.saveAll(transactionDetails);
    }

    private void createRollbackLedger(
            Transactions transaction,
            Wallet wallet,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(transaction.getTransactionId());
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setCurrency(wallet.getCurrency());
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setTxnType(transaction.getServiceCode());
        ledger.setReferenceType(OPERATION_NAME);
        ledger.setReferenceId(TraceContext.getTraceId());
        ledger.setDescription("Settlement rollback");
        walletLedgerRepository.save(ledger);
    }

    private void applyOptionalUpdates(String transactionId, SettleTransactionRequest request) {
        if (request.getComments() != null && !request.getComments().isBlank()) {
            transactionsService.updateComments(transactionId, request.getComments());
        }

        if (request.getAdditionalInfo() != null && !request.getAdditionalInfo().isEmpty()) {
            transactionsService.updateAdditionalInfo(transactionId, new JSONObject(request.getAdditionalInfo()));
        }
    }

    private SettleTransactionResponse buildResponse(Transactions transaction, Boolean settlementStatus) {
        return SettleTransactionResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(OPERATION_NAME)
                .code(Boolean.TRUE.equals(settlementStatus) ? "SETTLEMENT_SUCCESS" : "ROLLBACK_SUCCESS")
                .message(Boolean.TRUE.equals(settlementStatus)
                        ? "Transaction settled successfully"
                        : "Transaction rolled back successfully")
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transaction.getTransactionId())
                .transactionTraceId(transaction.getTraceId())
                .serviceCode(transaction.getServiceCode())
                .transferStatus(transaction.getTransferStatus())
                .build();
    }

    private WalletBalance lockBalance(Long walletId) {
        WalletBalance walletBalance = walletBalanceRepository.lockBalance(walletId);
        if (walletBalance == null) {
            throw new ApplicationException(
                    PaymentErrorCode.WALLET_BALANCE_NOT_FOUND,
                    null,
                    Map.of("walletId", walletId)
            );
        }
        return walletBalance;
    }

    private String getCurrentActorAccountId() {
        try {
            return JWTUtils.getCurrentAccountId();
        } catch (Exception ex) {
            return "SYSTEM";
        }
    }
}
