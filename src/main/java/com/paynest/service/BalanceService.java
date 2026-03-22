package com.paynest.service;


import com.paynest.dto.response.BalanceResponse;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.entity.*;
import com.paynest.enums.InitiatedBy;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.paynest.repository.*;
import com.paynest.tenant.TraceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
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

    public BalanceService(WalletRepository walletRepository,
                          WalletBalanceRepository balanceRepository, AccountRepository accountRepo, TransactionsRepository transactionsRepository,
                          TransactionDetailsRepository transactionDetailsRepository, PropertyReader propertyReader,
                          WalletBalanceRepository balanceRepo, WalletLedgerRepository ledgerRepo) {
        this.walletRepository = walletRepository;
        this.balanceRepository = balanceRepository;
        this.accountRepo = accountRepo;
        this.transactionsRepository = transactionsRepository;
        this.transactionDetailsRepository = transactionDetailsRepository;
        this.propertyReader = propertyReader;
        this.balanceRepo = balanceRepo;
        this.ledgerRepo = ledgerRepo;
    }

    public BalanceResponse getBalance(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO","Wallet not found"));

        WalletBalance balance = balanceRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO","Wallet not found"));

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
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT", "Account not found"));

        List<Wallet> walletList = walletRepository.findByAccountId(accountId);

        return walletList.stream()
                .map(wallet -> {
                    WalletBalance balance = balanceRepository.findById(wallet.getWalletId())
                            .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO", "Wallet not found"));
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
            InitiatedBy initiatedBy,
            String txnId) {

        BigDecimal currencyFactor =
                new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        LocalDateTime now = LocalDateTime.now();
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
                    ));
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

        WalletLedger debitLedger = new WalletLedger();
        debitLedger.setTxnId(txnId);
        debitLedger.setWalletId(debitorWallet.getWalletId());
        debitLedger.setAccountId(debitorWallet.getAccountId());
        debitLedger.setCurrency(debitorWallet.getCurrency());
        debitLedger.setEntryType(Constants.TXN_TYPE_DR);
        debitLedger.setAmount(dbAmount);
        debitLedger.setBalanceBefore(senderBalBefore);
        debitLedger.setBalanceAfter(senderBalAfter);
        debitLedger.setTxnType(serviceCode);
        debitLedger.setReferenceId(TraceContext.getTraceId());
        ledgerRepo.save(debitLedger);

        WalletLedger creditLedger = new WalletLedger();
        creditLedger.setTxnId(txnId);
        creditLedger.setWalletId(creditorWallet.getWalletId());
        creditLedger.setAccountId(creditorWallet.getAccountId());
        creditLedger.setCurrency(creditorWallet.getCurrency());
        creditLedger.setEntryType(Constants.TXN_TYPE_CR);
        creditLedger.setAmount(dbAmount);
        creditLedger.setBalanceBefore(receiverBalBefore);
        creditLedger.setBalanceAfter(receiverBalAfter);
        creditLedger.setTxnType(serviceCode);
        creditLedger.setReferenceId(TraceContext.getTraceId());
        ledgerRepo.save(creditLedger);

        debitorBalance.setAvailableBalance(senderBalAfter);
        creditorBalance.setAvailableBalance(receiverBalAfter);

        balanceRepo.save(debitorBalance);
        balanceRepo.save(creditorBalance);

        Transactions transaction = transactionsRepository.findByTransactionId(txnId);
        transaction.setTransferOn(now);
        transaction.setModifiedOn(now);
        if(initiatedBy == InitiatedBy.DEBITOR){
            transaction.setModifiedBy(debitorWallet.getAccountId());
        } else if (initiatedBy == InitiatedBy.CREDITOR) {
            transaction.setModifiedBy(creditorWallet.getAccountId());
        }
        transaction.setPreviousStatus(transaction.getTransferStatus());
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transactionsRepository.save(transaction);

        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        for (TransactionDetails transactionDetail : transactionDetails){
            transactionDetail.setTransferOn(now);
            transactionDetail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
            if(transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_DR)){
                transactionDetail.setPreviousBalance(senderBalBefore);
                transactionDetail.setPostBalance(senderBalAfter);
                transactionDetail.setPreviousFrozenBalance(senderFrozenBefore);
                transactionDetail.setPostFrozenBalance(senderFrozenAfter);
                transactionDetail.setPreviousFicBalance(senderFicBefore);
                transactionDetail.setPostFicBalance(senderFicAfter);
            }
            if (transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_CR)){
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

    @Transactional
    public void parkWalletAmountInFic(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId) {

        BigDecimal currencyFactor =
                new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        LocalDateTime now = LocalDateTime.now();
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
                    ));
        }

        BigDecimal receiverBalBefore = creditorBalance.getAvailableBalance();
        BigDecimal receiverFicBefore = creditorBalance.getFicBalance();
        BigDecimal receiverFrozenBefore = creditorBalance.getFrozenBalance();

        BigDecimal senderBalAfter = senderBalBefore.subtract(dbAmount);
        BigDecimal receiverBalAfter = receiverBalBefore.add(dbAmount);
        BigDecimal senderFicAfter = senderFicBefore;
        BigDecimal receiverFicAfter = receiverFicBefore.add(dbAmount);

        WalletLedger debitLedger = new WalletLedger();
        debitLedger.setTxnId(txnId);
        debitLedger.setWalletId(debitorWallet.getWalletId());
        debitLedger.setAccountId(debitorWallet.getAccountId());
        debitLedger.setCurrency(debitorWallet.getCurrency());
        debitLedger.setEntryType(Constants.TXN_TYPE_DR);
        debitLedger.setAmount(dbAmount);
        debitLedger.setBalanceBefore(senderBalBefore);
        debitLedger.setBalanceAfter(senderBalAfter);
        debitLedger.setTxnType(serviceCode);
        debitLedger.setReferenceId(TraceContext.getTraceId());
        ledgerRepo.save(debitLedger);

        WalletLedger creditLedger = new WalletLedger();
        creditLedger.setTxnId(txnId);
        creditLedger.setWalletId(creditorWallet.getWalletId());
        creditLedger.setAccountId(creditorWallet.getAccountId());
        creditLedger.setCurrency(creditorWallet.getCurrency());
        creditLedger.setEntryType(Constants.TXN_TYPE_CR);
        creditLedger.setAmount(dbAmount);
        creditLedger.setBalanceBefore(receiverBalBefore);
        creditLedger.setBalanceAfter(receiverBalAfter);
        creditLedger.setTxnType(serviceCode);
        creditLedger.setReferenceId(TraceContext.getTraceId());
        ledgerRepo.save(creditLedger);

        debitorBalance.setAvailableBalance(senderBalAfter);
        creditorBalance.setAvailableBalance(receiverBalAfter);
        creditorBalance.setFicBalance(receiverFicAfter);

        balanceRepo.save(debitorBalance);
        balanceRepo.save(creditorBalance);

        Transactions transaction = transactionsRepository.findByTransactionId(txnId);
        transaction.setTransferOn(now);
        transaction.setModifiedOn(now);
        if (initiatedBy == InitiatedBy.DEBITOR) {
            transaction.setModifiedBy(debitorWallet.getAccountId());
        } else if (initiatedBy == InitiatedBy.CREDITOR) {
            transaction.setModifiedBy(creditorWallet.getAccountId());
        }
        transaction.setPreviousStatus(transaction.getTransferStatus());
        transaction.setTransferStatus(Constants.TRANSACTION_AMBIGUOUS);
        transactionsRepository.save(transaction);

        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        for (TransactionDetails transactionDetail : transactionDetails) {
            transactionDetail.setTransferOn(now);
            transactionDetail.setTransferStatus(Constants.TRANSACTION_AMBIGUOUS);
            if (transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_DR)) {
                transactionDetail.setPreviousBalance(senderBalBefore);
                transactionDetail.setPostBalance(senderBalAfter);
                transactionDetail.setPreviousFrozenBalance(senderFrozenBefore);
                transactionDetail.setPostFrozenBalance(senderFrozenBefore);
                transactionDetail.setPreviousFicBalance(senderFicBefore);
                transactionDetail.setPostFicBalance(senderFicAfter);
            }
            if (transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_CR)) {
                transactionDetail.setPreviousBalance(receiverBalBefore);
                transactionDetail.setPostBalance(receiverBalAfter);
                transactionDetail.setPreviousFrozenBalance(receiverFrozenBefore);
                transactionDetail.setPostFrozenBalance(receiverFrozenBefore);
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
}
