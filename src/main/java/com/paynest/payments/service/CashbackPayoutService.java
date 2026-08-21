package com.paynest.payments.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TenantTime;
import com.paynest.limits.service.TransactionLimitValidator;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.entity.CashbackPayout;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.repository.CashbackPayoutRepository;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashbackPayoutService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FAILED = "FAILED";

    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final String COMMISSION_WALLET_TYPE = "COMMDIS";
    private static final String BONUS_WALLET_TYPE = "BONUS";
    private static final String MAIN_WALLET_TYPE = "MAIN";
    private static final String CASHBACK_SERVICE_CODE = "CASHBACK";

    private final CashbackPayoutRepository cashbackPayoutRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository balanceRepository;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final WalletLedgerRepository ledgerRepository;
    private final PropertyReader propertyReader;
    private final WalletCacheService walletCacheService;
    private final TransactionNotificationEventPublisher transactionNotificationEventPublisher;
    private final TransactionLimitValidator transactionLimitValidator;

    public void payoutDueCashback(LocalDateTime dueAt) {
        LocalDateTime effectiveDueAt = dueAt == null ? TenantTime.now() : dueAt;
        cashbackPayoutRepository.findTop100ByStatusAndPayAtLessThanEqualOrderByPayAtAsc(
                        STATUS_PENDING,
                        effectiveDueAt
                )
                .forEach(candidate -> payoutCashback(candidate.getCashbackPayoutId()));
    }

    @Transactional
    public void payoutCashback(Long cashbackPayoutId) {
        CashbackPayout payout = cashbackPayoutRepository
                .findFirstByCashbackPayoutIdAndStatus(cashbackPayoutId, STATUS_PENDING)
                .orElse(null);
        if (payout == null) {
            return;
        }

        try {
            Wallet sourceWallet = getActiveSystemCommissionWallet(payout.getCurrency());
            Wallet beneficiaryWallet = resolveBeneficiaryWallet(
                    payout.getBeneficiaryAccountId(),
                    payout.getCurrency()
            );

            BigDecimal dbAmount = payout.getAmount();
            String payoutTxnId = newTransactionId();
            LocalDateTime now = TenantTime.now();

            Map<Long, WalletBalance> lockedBalances = lockBalancesInWalletIdOrder(sourceWallet, beneficiaryWallet);
            WalletBalance sourceBalance = lockedBalances.get(sourceWallet.getWalletId());
            WalletBalance beneficiaryBalance = lockedBalances.get(beneficiaryWallet.getWalletId());

            BigDecimal sourceBalBefore = sourceBalance.getAvailableBalance();
            BigDecimal sourceBalAfter = sourceBalBefore.subtract(dbAmount);
            BigDecimal beneficiaryBalBefore = beneficiaryBalance.getAvailableBalance();
            BigDecimal beneficiaryBalAfter = beneficiaryBalBefore.add(dbAmount);

            transactionLimitValidator.validateAndReserve(
                    sourceWallet,
                    beneficiaryWallet,
                    dbAmount,
                    dbAmount,
                    sourceBalAfter,
                    beneficiaryBalAfter,
                    CASHBACK_SERVICE_CODE,
                    "SYSTEM",
                    payoutTxnId
            );

            sourceBalance.setAvailableBalance(sourceBalAfter);
            beneficiaryBalance.setAvailableBalance(beneficiaryBalAfter);
            balanceRepository.save(sourceBalance);
            balanceRepository.save(beneficiaryBalance);

            saveLedgerEntry(
                    payoutTxnId,
                    sourceWallet,
                    Constants.TXN_TYPE_DR,
                    dbAmount,
                    sourceBalBefore,
                    sourceBalAfter,
                    payout
            );
            saveLedgerEntry(
                    payoutTxnId,
                    beneficiaryWallet,
                    Constants.TXN_TYPE_CR,
                    dbAmount,
                    beneficiaryBalBefore,
                    beneficiaryBalAfter,
                    payout
            );

            Transactions transaction = buildTransaction(
                    payoutTxnId,
                    sourceWallet,
                    beneficiaryWallet,
                    dbAmount,
                    payout,
                    now
            );
            transactionsRepository.save(transaction);

            transactionDetailsRepository.saveAll(java.util.List.of(
                    buildTransactionDetail(
                            payoutTxnId,
                            1L,
                            sourceWallet,
                            Constants.TXN_TYPE_DR,
                            "SYSTEM",
                            sourceWallet.getWalletId().toString(),
                            beneficiaryWallet.getWalletId().toString(),
                            dbAmount,
                            sourceBalBefore,
                            sourceBalAfter,
                            sourceBalance.getFrozenBalance(),
                            sourceBalance.getFicBalance(),
                            now
                    ),
                    buildTransactionDetail(
                            payoutTxnId,
                            2L,
                            beneficiaryWallet,
                            Constants.TXN_TYPE_CR,
                            "SUBSCRIBER",
                            payout.getBeneficiaryAccountId(),
                            sourceWallet.getWalletId().toString(),
                            dbAmount,
                            beneficiaryBalBefore,
                            beneficiaryBalAfter,
                            beneficiaryBalance.getFrozenBalance(),
                            beneficiaryBalance.getFicBalance(),
                            now
                    )
            ));

            payout.setPayoutTransactionId(payoutTxnId);
            payout.setStatus(STATUS_PAID);
            payout.setFailureReason(null);
            cashbackPayoutRepository.save(payout);

            walletCacheService.refreshAccountWallets(sourceWallet.getAccountId());
            walletCacheService.refreshAccountWallets(beneficiaryWallet.getAccountId());
            transactionNotificationEventPublisher.publish(transaction);
        } catch (Exception ex) {
            payout.setStatus(STATUS_FAILED);
            payout.setFailureReason(truncate(ex.getMessage(), 300));
            cashbackPayoutRepository.save(payout);
            log.error("Failed to payout cashback id={}", cashbackPayoutId, ex);
        }
    }

    private Transactions buildTransaction(
            String payoutTxnId,
            Wallet sourceWallet,
            Wallet beneficiaryWallet,
            BigDecimal dbAmount,
            CashbackPayout payout,
            LocalDateTime now
    ) {
        Transactions transaction = new Transactions();
        transaction.setTransactionId(payoutTxnId);
        transaction.setTransferOn(now);
        transaction.setTransactionValue(dbAmount);
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transaction.setRequestGateway("SYSTEM");
        transaction.setServiceCode(CASHBACK_SERVICE_CODE);
        transaction.setCreatedBy(SYSTEM_ACCOUNT_ID);
        transaction.setModifiedBy(SYSTEM_ACCOUNT_ID);
        transaction.setCreatedOn(now);
        transaction.setModifiedOn(now);
        transaction.setDebitorAccountId(sourceWallet.getAccountId());
        transaction.setCreditorAccountId(beneficiaryWallet.getAccountId());
        transaction.setDebitorWalletType(sourceWallet.getWalletType());
        transaction.setDebitorCurrency(sourceWallet.getCurrency());
        transaction.setCreditorWalletType(beneficiaryWallet.getWalletType());
        transaction.setCreditorCurrency(beneficiaryWallet.getCurrency());
        transaction.setPaymentReference(payout.getOriginalTransactionId());
        transaction.setComments("Cashback payout for " + payout.getOriginalTransactionId());
        return transaction;
    }

    private TransactionDetails buildTransactionDetail(
            String txnId,
            Long sequenceNumber,
            Wallet wallet,
            String entryType,
            String userType,
            String identifierId,
            String secondIdentifierId,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            BigDecimal frozenBalance,
            BigDecimal ficBalance,
            LocalDateTime now
    ) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(txnId, sequenceNumber));
        detail.setAccountId(wallet.getAccountId());
        detail.setUserType(userType);
        detail.setEntryType(entryType);
        detail.setTransactionType(Constants.TXN_TYPE_DR.equalsIgnoreCase(entryType)
                ? Constants.TXN_DETAIL_TYPE_MONEY_PAID
                : Constants.TXN_DETAIL_TYPE_MONEY_RECEIVED);
        detail.setIdentifierId(identifierId);
        detail.setSecondIdentifierId(secondIdentifierId);
        detail.setTransactionValue(amount);
        detail.setApprovedValue(amount);
        detail.setPreviousBalance(balanceBefore);
        detail.setPostBalance(balanceAfter);
        detail.setPreviousFrozenBalance(frozenBalance);
        detail.setPostFrozenBalance(frozenBalance);
        detail.setPreviousFicBalance(ficBalance);
        detail.setPostFicBalance(ficBalance);
        detail.setTransferOn(now);
        detail.setServiceCode(CASHBACK_SERVICE_CODE);
        detail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        detail.setWalletNumber(wallet.getWalletId().toString());
        detail.setWalletType(wallet.getWalletType());
        detail.setCurrency(wallet.getCurrency());
        return detail;
    }

    private void saveLedgerEntry(
            String txnId,
            Wallet wallet,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            CashbackPayout payout
    ) {
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(txnId);
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setCurrency(wallet.getCurrency());
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setTxnType(CASHBACK_SERVICE_CODE);
        ledger.setReferenceType("CASHBACK_PAYOUT");
        ledger.setReferenceId(String.valueOf(payout.getCashbackPayoutId()));
        ledger.setDescription("Cashback payout for originalTransactionId=" + payout.getOriginalTransactionId());
        ledgerRepository.save(ledger);
    }

    private Wallet getActiveSystemCommissionWallet(String currency) {
        return getActiveWallet(SYSTEM_ACCOUNT_ID, currency, COMMISSION_WALLET_TYPE)
                .orElseThrow(() -> new IllegalStateException("SYS0001 commission wallet not found"));
    }

    private Wallet resolveBeneficiaryWallet(String accountId, String currency) {
        return getActiveWallet(accountId, currency, BONUS_WALLET_TYPE)
                .or(() -> getActiveWallet(accountId, currency, MAIN_WALLET_TYPE))
                .orElseThrow(() -> new IllegalStateException("Beneficiary bonus/main wallet not found"));
    }

    private Optional<Wallet> getActiveWallet(String accountId, String currency, String walletType) {
        return walletRepository.findByAccountIdAndCurrencyAndWalletType(accountId, currency, walletType)
                .filter(wallet -> Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus()))
                .filter(wallet -> !Boolean.TRUE.equals(wallet.getIsLocked()));
    }

    private Map<Long, WalletBalance> lockBalancesInWalletIdOrder(Wallet... wallets) {
        Set<Long> walletIds = java.util.Arrays.stream(wallets)
                .map(Wallet::getWalletId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return walletIds.stream()
                .sorted()
                .collect(Collectors.toMap(
                        walletId -> walletId,
                        balanceRepository::lockBalance,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ));
    }

    private String newTransactionId() {
        return ("CB" + UUID.randomUUID().toString().replace("-", "")).substring(0, 30);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
