package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.users.dto.response.BalanceResponse;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BalanceService {

    private final WalletRepository walletRepository;
    private final WalletBalanceRepository balanceRepository;
    private final AccountRepository accountRepo;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final PropertyReader propertyReader;
    private final WalletBalanceRepository balanceRepo;
    private final WalletLedgerRepository ledgerRepo;
    private final TransactionsService transactionsService;
    private final WalletCacheService walletCacheService;

    public BalanceService(WalletRepository walletRepository,
                          WalletBalanceRepository balanceRepository,
                          AccountRepository accountRepo,
                          TransactionsRepository transactionsRepository,
                          TransactionDetailsRepository transactionDetailsRepository,
                          PropertyReader propertyReader,
                          WalletBalanceRepository balanceRepo,
                          WalletLedgerRepository ledgerRepo,
                          TransactionsService transactionsService,
                          WalletCacheService walletCacheService) {
        this.walletRepository = walletRepository;
        this.balanceRepository = balanceRepository;
        this.accountRepo = accountRepo;
        this.transactionsRepository = transactionsRepository;
        this.transactionDetailsRepository = transactionDetailsRepository;
        this.propertyReader = propertyReader;
        this.balanceRepo = balanceRepo;
        this.ledgerRepo = ledgerRepo;
        this.transactionsService = transactionsService;
        this.walletCacheService = walletCacheService;
    }

    public BalanceResponse getBalance(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO, "Wallet not found"));

        WalletBalance balance = balanceRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO, "Wallet not found"));

        return new BalanceResponse(
                wallet.getWalletType(),
                wallet.getCurrency(),
                balance.getAvailableBalance(),
                balance.getFrozenBalance(),
                balance.getFicBalance()
        );
    }

    @Transactional
    public List<BalanceResponse> getAllWalletBalance(String accountId) {

        accountRepo.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        List<Wallet> walletList = walletRepository.findByAccountId(accountId);

        return walletList.stream()
                .map(wallet -> {
                    WalletBalance balance = balanceRepository.findById(wallet.getWalletId())
                            .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO, "Wallet not found"));
                    return new BalanceResponse(
                            wallet.getWalletType(),
                            wallet.getCurrency(),
                            balance.getAvailableBalance(),
                            balance.getFrozenBalance(),
                            balance.getFicBalance()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void transferWalletAmount(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String serviceCode,
            String txnId) {
        transferWalletAmount(
                debitorWallet,
                creditorWallet,
                amount,
                serviceCode,
                null,
                txnId
        );
    }

    @Transactional
    public void transferWalletAmount(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId) {

        try {
            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
            LocalDateTime now = TenantTime.now();
            BigDecimal dbAmount = amount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            boolean lockDebitorFirst = debitorWallet.getWalletId() <= creditorWallet.getWalletId();
            WalletBalance firstLockedBalance = lockBalance(lockDebitorFirst
                    ? debitorWallet.getWalletId()
                    : creditorWallet.getWalletId());
            WalletBalance secondLockedBalance = lockBalance(lockDebitorFirst
                    ? creditorWallet.getWalletId()
                    : debitorWallet.getWalletId());

            WalletBalance debitorBalance = lockDebitorFirst ? firstLockedBalance : secondLockedBalance;
            WalletBalance creditorBalance = lockDebitorFirst ? secondLockedBalance : firstLockedBalance;

            BigDecimal senderBalBefore = debitorBalance.getAvailableBalance();
            BigDecimal senderFicBefore = debitorBalance.getFicBalance();
            BigDecimal senderFrozenBefore = debitorBalance.getFrozenBalance();

            BigDecimal senderNetBalance = senderBalBefore
                    .subtract(senderFicBefore)
                    .subtract(senderFrozenBefore);

            if (requiresBalanceCheck(debitorWallet) && senderNetBalance.compareTo(dbAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", dbAmount.toPlainString(),
                                "walletId", debitorWallet.getWalletId()
                        )
                );
            }

            BigDecimal receiverBalBefore = creditorBalance.getAvailableBalance();
            BigDecimal receiverFicBefore = creditorBalance.getFicBalance();
            BigDecimal receiverFrozenBefore = creditorBalance.getFrozenBalance();

            BigDecimal senderBalAfter = senderBalBefore.subtract(dbAmount);
            BigDecimal receiverBalAfter = receiverBalBefore.add(dbAmount);

            BigDecimal senderFicAfter = senderFicBefore;
            BigDecimal senderFrozenAfter = senderFrozenBefore;

            BigDecimal receiverFicAfter = receiverFicBefore;
            BigDecimal receiverFrozenAfter = receiverFrozenBefore;

            saveLedgerEntry(
                    txnId,
                    debitorWallet,
                    Constants.TXN_TYPE_DR,
                    dbAmount,
                    senderBalBefore,
                    senderBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    creditorWallet,
                    Constants.TXN_TYPE_CR,
                    dbAmount,
                    receiverBalBefore,
                    receiverBalAfter,
                    serviceCode
            );

            debitorBalance.setAvailableBalance(senderBalAfter);
            creditorBalance.setAvailableBalance(receiverBalAfter);

            balanceRepo.save(debitorBalance);
            balanceRepo.save(creditorBalance);

            Transactions transaction = transactionsRepository.findByTransactionId(txnId);
            if (transaction != null) {
                transaction.setTransferOn(now);
                transaction.setModifiedOn(now);
                transaction.setModifiedBy(resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet));
                transaction.setPreviousStatus(transaction.getTransferStatus());
                transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
                transactionsRepository.save(transaction);
            }

            updateTransactionDetails(
                    txnId,
                    now,
                    Constants.TRANSACTION_SUCCESS,
                    senderBalBefore,
                    senderBalAfter,
                    senderFrozenBefore,
                    senderFrozenAfter,
                    senderFicBefore,
                    senderFicAfter,
                    receiverBalBefore,
                    receiverBalAfter,
                    receiverFrozenBefore,
                    receiverFrozenAfter,
                    receiverFicBefore,
                    receiverFicAfter
            );

            walletCacheService.refreshAccountWallets(debitorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
        } catch (ApplicationException ex) {
            transactionsService.updateFailedTransactionRecord(
                    txnId,
                    ex.getErrorCode(),
                    resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet)
            );
            throw ex;
        } catch (Exception ex) {
            transactionsService.updateFailedTransactionRecord(
                    txnId,
                    ErrorCodes.SYSTEM_ERROR,
                    resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet)
            );
            throw ex;
        }
    }

    @Transactional
    public void parkWalletAmountInFic(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId) {

        try {
            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
            LocalDateTime now = TenantTime.now();
            BigDecimal dbAmount = amount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            boolean lockDebitorFirst = debitorWallet.getWalletId() <= creditorWallet.getWalletId();
            WalletBalance firstLockedBalance = lockBalance(lockDebitorFirst
                    ? debitorWallet.getWalletId()
                    : creditorWallet.getWalletId());
            WalletBalance secondLockedBalance = lockBalance(lockDebitorFirst
                    ? creditorWallet.getWalletId()
                    : debitorWallet.getWalletId());

            WalletBalance debitorBalance = lockDebitorFirst ? firstLockedBalance : secondLockedBalance;
            WalletBalance creditorBalance = lockDebitorFirst ? secondLockedBalance : firstLockedBalance;

            BigDecimal senderBalBefore = debitorBalance.getAvailableBalance();
            BigDecimal senderFicBefore = debitorBalance.getFicBalance();
            BigDecimal senderFrozenBefore = debitorBalance.getFrozenBalance();

            BigDecimal senderNetBalance = senderBalBefore
                    .subtract(senderFicBefore)
                    .subtract(senderFrozenBefore);

            if (senderNetBalance.compareTo(dbAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", dbAmount.toPlainString(),
                                "walletId", debitorWallet.getWalletId()
                        )
                );
            }

            BigDecimal receiverBalBefore = creditorBalance.getAvailableBalance();
            BigDecimal receiverFicBefore = creditorBalance.getFicBalance();
            BigDecimal receiverFrozenBefore = creditorBalance.getFrozenBalance();

            BigDecimal senderBalAfter = senderBalBefore.subtract(dbAmount);
            BigDecimal receiverBalAfter = receiverBalBefore.add(dbAmount);
            BigDecimal senderFicAfter = senderFicBefore;
            BigDecimal receiverFicAfter = receiverFicBefore.add(dbAmount);

            saveLedgerEntry(
                    txnId,
                    debitorWallet,
                    Constants.TXN_TYPE_DR,
                    dbAmount,
                    senderBalBefore,
                    senderBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    creditorWallet,
                    Constants.TXN_TYPE_CR,
                    dbAmount,
                    receiverBalBefore,
                    receiverBalAfter,
                    serviceCode
            );

            debitorBalance.setAvailableBalance(senderBalAfter);
            creditorBalance.setAvailableBalance(receiverBalAfter);
            creditorBalance.setFicBalance(receiverFicAfter);

            balanceRepo.save(debitorBalance);
            balanceRepo.save(creditorBalance);

            Transactions transaction = transactionsRepository.findByTransactionId(txnId);
            if (transaction != null) {
                transaction.setTransferOn(now);
                transaction.setModifiedOn(now);
                transaction.setModifiedBy(resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet));
                transaction.setPreviousStatus(transaction.getTransferStatus());
                transaction.setTransferStatus(Constants.TRANSACTION_AMBIGUOUS);
                transactionsRepository.save(transaction);
            }

            updateTransactionDetails(
                    txnId,
                    now,
                    Constants.TRANSACTION_AMBIGUOUS,
                    senderBalBefore,
                    senderBalAfter,
                    senderFrozenBefore,
                    senderFrozenBefore,
                    senderFicBefore,
                    senderFicAfter,
                    receiverBalBefore,
                    receiverBalAfter,
                    receiverFrozenBefore,
                    receiverFrozenBefore,
                    receiverFicBefore,
                    receiverFicAfter
            );

            walletCacheService.refreshAccountWallets(debitorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
        } catch (ApplicationException ex) {
            transactionsService.updateFailedTransactionRecord(
                    txnId,
                    ex.getErrorCode(),
                    resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet)
            );
            throw ex;
        } catch (Exception ex) {
            transactionsService.updateFailedTransactionRecord(
                    txnId,
                    ErrorCodes.SYSTEM_ERROR,
                    resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet)
            );
            throw ex;
        }
    }

    private void saveLedgerEntry(
            String txnId,
            Wallet wallet,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String serviceCode
    ) {
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(txnId);
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setCurrency(wallet.getCurrency());
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setTxnType(serviceCode);
        ledger.setReferenceId(TraceContext.getTraceId());
        ledgerRepo.save(ledger);
    }

    private void updateTransactionDetails(
            String txnId,
            LocalDateTime now,
            String status,
            BigDecimal senderBalBefore,
            BigDecimal senderBalAfter,
            BigDecimal senderFrozenBefore,
            BigDecimal senderFrozenAfter,
            BigDecimal senderFicBefore,
            BigDecimal senderFicAfter,
            BigDecimal receiverBalBefore,
            BigDecimal receiverBalAfter,
            BigDecimal receiverFrozenBefore,
            BigDecimal receiverFrozenAfter,
            BigDecimal receiverFicBefore,
            BigDecimal receiverFicAfter
    ) {
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        for (TransactionDetails transactionDetail : transactionDetails) {
            transactionDetail.setTransferOn(now);
            transactionDetail.setTransferStatus(status);
            if (transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_DR)) {
                transactionDetail.setPreviousBalance(senderBalBefore);
                transactionDetail.setPostBalance(senderBalAfter);
                transactionDetail.setPreviousFrozenBalance(senderFrozenBefore);
                transactionDetail.setPostFrozenBalance(senderFrozenAfter);
                transactionDetail.setPreviousFicBalance(senderFicBefore);
                transactionDetail.setPostFicBalance(senderFicAfter);
            }
            if (transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_CR)) {
                transactionDetail.setPreviousBalance(receiverBalBefore);
                transactionDetail.setPostBalance(receiverBalAfter);
                transactionDetail.setPreviousFrozenBalance(receiverFrozenBefore);
                transactionDetail.setPostFrozenBalance(receiverFrozenAfter);
                transactionDetail.setPreviousFicBalance(receiverFicBefore);
                transactionDetail.setPostFicBalance(receiverFicAfter);
            }
        }
        transactionDetailsRepository.saveAll(transactionDetails);
    }

    private WalletBalance lockBalance(Long walletId) {
        WalletBalance walletBalance = balanceRepo.lockBalance(walletId);
        if (walletBalance == null) {
            throw new ApplicationException(
                    PaymentErrorCode.WALLET_BALANCE_NOT_FOUND,
                    null,
                    Map.of("walletId", walletId)
            );
        }
        return walletBalance;
    }

    private boolean requiresBalanceCheck(Wallet wallet) {
        return !(wallet.getWalletType().equalsIgnoreCase("BANK")
                || wallet.getWalletType().equalsIgnoreCase("COMMDIS"));
    }

    private String resolveActorAccountId(
            InitiatedBy initiatedBy,
            Wallet debitorWallet,
            Wallet creditorWallet
    ) {
        if (initiatedBy == InitiatedBy.CREDITOR && creditorWallet != null) {
            return creditorWallet.getAccountId();
        }
        if (debitorWallet != null) {
            return debitorWallet.getAccountId();
        }
        return creditorWallet == null ? null : creditorWallet.getAccountId();
    }
}
