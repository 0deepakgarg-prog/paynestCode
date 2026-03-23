package com.paynest.payments.service;


import com.paynest.users.dto.response.BalanceResponse;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final TransactionsService transactionsService;
    private final WalletCacheService walletCacheService;


    public BalanceService(WalletRepository walletRepository,
                          WalletBalanceRepository balanceRepository, AccountRepository accountRepo, TransactionsRepository transactionsRepository,
                          TransactionDetailsRepository transactionDetailsRepository, PropertyReader propertyReader,
                          WalletBalanceRepository balanceRepo, WalletLedgerRepository ledgerRepo,
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
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO,"Wallet not found"));

        WalletBalance balance = balanceRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO,"Wallet not found"));

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
            String txnId) throws ApplicationException {

        try {
            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));

            BigDecimal dbAmount = amount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            WalletBalance debitorBalance =
                    balanceRepo.lockBalance(debitorWallet.getWalletId());

            WalletBalance creditorBalance =
                    balanceRepo.lockBalance(creditorWallet.getWalletId());

            BigDecimal senderBalBefore = debitorBalance.getAvailableBalance();
            BigDecimal senderFicBefore = debitorBalance.getFicBalance();
            BigDecimal senderFrozenBefore = debitorBalance.getFrozenBalance();

            BigDecimal senderNetBalance = senderBalBefore
                    .subtract(senderFicBefore)
                    .subtract(senderFrozenBefore);

            if(!(debitorWallet.getWalletType().equalsIgnoreCase("BANK") ||
                    debitorWallet.getWalletType().equalsIgnoreCase("COMMDIS"))) {
                if (senderNetBalance.compareTo(dbAmount) < 0) {
                    transactionsService.updateFailedTransactionRecord(txnId, ErrorCodes.INSUFFICIENT_BALANCE, debitorWallet.getAccountId());
                    throw new ApplicationException(
                            ErrorCodes.INSUFFICIENT_BALANCE,
                            "Insufficient balance");
                }
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

            transactionsRepository.updateStatus(
                    txnId,
                    Constants.TRANSACTION_SUCCESS,
                    null);

            transactionDetailsRepository.updateBalances(
                    txnId,
                    1L,
                    senderBalBefore,
                    senderBalAfter,
                    senderFicBefore,
                    senderFicAfter,
                    senderFrozenBefore,
                    senderFrozenAfter,
                    Constants.TRANSACTION_SUCCESS
            );

            transactionDetailsRepository.updateBalances(
                    txnId,
                    2L,
                    receiverBalBefore,
                    receiverBalAfter,
                    receiverFicBefore,
                    receiverFicAfter,
                    receiverFrozenBefore,
                    receiverFrozenAfter,
                    Constants.TRANSACTION_SUCCESS
            );

            walletCacheService.refreshAccountWallets(debitorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());

        }
        catch (ApplicationException ex) {

            transactionsRepository.updateStatus(
                    txnId,
                    Constants.TRANSACTION_FAILED,
                    ex.getErrorCode());

            throw ex;
        }
        catch (Exception ex) {

            transactionsRepository.updateStatus(
                    txnId,
                    Constants.TRANSACTION_FAILED,
                    ErrorCodes.SYSTEM_ERROR);

            throw ex;
        }
    }
}

