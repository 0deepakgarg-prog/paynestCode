package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.payments.validation.WalletRestrictionValidator;
import com.paynest.payments.service.TransactionsService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
    private final TransactionNotificationEventPublisher transactionNotificationEventPublisher;
    private final WalletRestrictionValidator walletRestrictionValidator;

    public BalanceService(WalletRepository walletRepository,
                          WalletBalanceRepository balanceRepository,
                          AccountRepository accountRepo,
                          TransactionsRepository transactionsRepository,
                          TransactionDetailsRepository transactionDetailsRepository,
                          PropertyReader propertyReader,
                          WalletBalanceRepository balanceRepo,
                          WalletLedgerRepository ledgerRepo,
                          TransactionsService transactionsService,
                          WalletCacheService walletCacheService,
                          TransactionNotificationEventPublisher transactionNotificationEventPublisher,
                          WalletRestrictionValidator walletRestrictionValidator) {
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
        this.transactionNotificationEventPublisher = transactionNotificationEventPublisher;
        this.walletRestrictionValidator = walletRestrictionValidator;
    }

    public BalanceResponse getBalance(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO, "Wallet not found"));

        WalletBalance balance = balanceRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_WALLET_NO, "Wallet not found"));

        return new BalanceResponse(
                wallet.getWalletType(),
                wallet.getCurrency(),
                toDisplayAmount(balance.getAvailableBalance()),
                toDisplayAmount(balance.getFrozenBalance()),
                toDisplayAmount(balance.getFicBalance())
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
                            toDisplayAmount(balance.getAvailableBalance()),
                            toDisplayAmount(balance.getFrozenBalance()),
                            toDisplayAmount(balance.getFicBalance())
                    );
                })
                .collect(Collectors.toList());
    }

    private BigDecimal toDisplayAmount(BigDecimal storedAmount) {
        if (storedAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        return storedAmount.divide(currencyFactor, 2, RoundingMode.HALF_UP);
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
            walletRestrictionValidator.validateTransfer(debitorWallet, creditorWallet, serviceCode);

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
                transactionNotificationEventPublisher.publish(transaction);
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
    public void transferCrossCurrencyWalletAmount(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId) {

        try {
            walletRestrictionValidator.validateTransfer(debitorWallet, creditorWallet, serviceCode);

            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
            LocalDateTime now = TenantTime.now();
            BigDecimal debitDbAmount = debitAmount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal creditDbAmount = creditAmount
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

            if (requiresBalanceCheck(debitorWallet) && senderNetBalance.compareTo(debitDbAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", debitDbAmount.toPlainString(),
                                "walletId", debitorWallet.getWalletId()
                        )
                );
            }

            BigDecimal receiverBalBefore = creditorBalance.getAvailableBalance();
            BigDecimal receiverFicBefore = creditorBalance.getFicBalance();
            BigDecimal receiverFrozenBefore = creditorBalance.getFrozenBalance();

            BigDecimal senderBalAfter = senderBalBefore.subtract(debitDbAmount);
            BigDecimal receiverBalAfter = receiverBalBefore.add(creditDbAmount);

            saveLedgerEntry(
                    txnId,
                    debitorWallet,
                    Constants.TXN_TYPE_DR,
                    debitDbAmount,
                    senderBalBefore,
                    senderBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    creditorWallet,
                    Constants.TXN_TYPE_CR,
                    creditDbAmount,
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
                transactionNotificationEventPublisher.publish(transaction);
            }

            updateTransactionDetails(
                    txnId,
                    now,
                    Constants.TRANSACTION_SUCCESS,
                    senderBalBefore,
                    senderBalAfter,
                    senderFrozenBefore,
                    senderFrozenBefore,
                    senderFicBefore,
                    senderFicBefore,
                    receiverBalBefore,
                    receiverBalAfter,
                    receiverFrozenBefore,
                    receiverFrozenBefore,
                    receiverFicBefore,
                    receiverFicBefore
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
    public void transferCurrencyExchangeWalletAmount(
            Wallet debitorWallet,
            Wallet systemSourceWallet,
            Wallet systemTargetWallet,
            Wallet creditorWallet,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId) {

        try {
            walletRestrictionValidator.validateTransfer(debitorWallet, creditorWallet, serviceCode);

            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
            LocalDateTime now = TenantTime.now();
            BigDecimal debitDbAmount = debitAmount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal creditDbAmount = creditAmount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            validateCurrencyExchangeTransactionDetails(txnId);

            Map<Long, WalletBalance> lockedBalances = lockBalances(
                    debitorWallet,
                    systemSourceWallet,
                    systemTargetWallet,
                    creditorWallet
            );
            WalletBalance debitorBalance = lockedBalances.get(debitorWallet.getWalletId());
            WalletBalance systemSourceBalance = lockedBalances.get(systemSourceWallet.getWalletId());
            WalletBalance systemTargetBalance = lockedBalances.get(systemTargetWallet.getWalletId());
            WalletBalance creditorBalance = lockedBalances.get(creditorWallet.getWalletId());

            BigDecimal debitorBalBefore = debitorBalance.getAvailableBalance();
            BigDecimal debitorFicBefore = debitorBalance.getFicBalance();
            BigDecimal debitorFrozenBefore = debitorBalance.getFrozenBalance();
            BigDecimal debitorNetBalance = debitorBalBefore
                    .subtract(debitorFicBefore)
                    .subtract(debitorFrozenBefore);

            if (requiresBalanceCheck(debitorWallet) && debitorNetBalance.compareTo(debitDbAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", debitDbAmount.toPlainString(),
                                "walletId", debitorWallet.getWalletId()
                        )
                );
            }

            BigDecimal systemSourceBalBefore = systemSourceBalance.getAvailableBalance();
            BigDecimal systemSourceFicBefore = systemSourceBalance.getFicBalance();
            BigDecimal systemSourceFrozenBefore = systemSourceBalance.getFrozenBalance();
            BigDecimal systemTargetBalBefore = systemTargetBalance.getAvailableBalance();
            BigDecimal systemTargetFicBefore = systemTargetBalance.getFicBalance();
            BigDecimal systemTargetFrozenBefore = systemTargetBalance.getFrozenBalance();
            BigDecimal creditorBalBefore = creditorBalance.getAvailableBalance();
            BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
            BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();

            BigDecimal systemTargetNetBalance = systemTargetBalBefore
                    .subtract(systemTargetFicBefore)
                    .subtract(systemTargetFrozenBefore);
            if (requiresBalanceCheck(systemTargetWallet) && systemTargetNetBalance.compareTo(creditDbAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", creditDbAmount.toPlainString(),
                                "walletId", systemTargetWallet.getWalletId()
                        )
                );
            }

            BigDecimal debitorBalAfter = debitorBalBefore.subtract(debitDbAmount);
            BigDecimal systemSourceBalAfter = systemSourceBalBefore.add(debitDbAmount);
            BigDecimal systemTargetBalAfter = systemTargetBalBefore.subtract(creditDbAmount);
            BigDecimal creditorBalAfter = creditorBalBefore.add(creditDbAmount);

            saveLedgerEntry(
                    txnId,
                    debitorWallet,
                    Constants.TXN_TYPE_DR,
                    debitDbAmount,
                    debitorBalBefore,
                    debitorBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    systemSourceWallet,
                    Constants.TXN_TYPE_CR,
                    debitDbAmount,
                    systemSourceBalBefore,
                    systemSourceBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    systemTargetWallet,
                    Constants.TXN_TYPE_DR,
                    creditDbAmount,
                    systemTargetBalBefore,
                    systemTargetBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    creditorWallet,
                    Constants.TXN_TYPE_CR,
                    creditDbAmount,
                    creditorBalBefore,
                    creditorBalAfter,
                    serviceCode
            );

            debitorBalance.setAvailableBalance(debitorBalAfter);
            systemSourceBalance.setAvailableBalance(systemSourceBalAfter);
            systemTargetBalance.setAvailableBalance(systemTargetBalAfter);
            creditorBalance.setAvailableBalance(creditorBalAfter);

            balanceRepo.save(debitorBalance);
            balanceRepo.save(systemSourceBalance);
            balanceRepo.save(systemTargetBalance);
            balanceRepo.save(creditorBalance);

            Transactions transaction = transactionsRepository.findByTransactionId(txnId);
            if (transaction != null) {
                transaction.setTransferOn(now);
                transaction.setModifiedOn(now);
                transaction.setModifiedBy(resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet));
                transaction.setPreviousStatus(transaction.getTransferStatus());
                transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
                transactionsRepository.save(transaction);
                transactionNotificationEventPublisher.publish(transaction);
            }

            updateCurrencyExchangeTransactionDetails(
                    txnId,
                    now,
                    Constants.TRANSACTION_SUCCESS,
                    debitorBalBefore,
                    debitorBalAfter,
                    debitorFrozenBefore,
                    debitorFicBefore,
                    systemSourceBalBefore,
                    systemSourceBalAfter,
                    systemSourceFrozenBefore,
                    systemSourceFicBefore,
                    systemTargetBalBefore,
                    systemTargetBalAfter,
                    systemTargetFrozenBefore,
                    systemTargetFicBefore,
                    creditorBalBefore,
                    creditorBalAfter,
                    creditorFrozenBefore,
                    creditorFicBefore
            );

            walletCacheService.refreshAccountWallets(debitorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(systemSourceWallet.getAccountId());
            walletCacheService.refreshAccountWallets(systemTargetWallet.getAccountId());
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
            walletRestrictionValidator.validateTransfer(debitorWallet, creditorWallet, serviceCode);

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
                transactionNotificationEventPublisher.publish(transaction);
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

    private void updateCurrencyExchangeTransactionDetails(
            String txnId,
            LocalDateTime now,
            String status,
            BigDecimal debitorBalBefore,
            BigDecimal debitorBalAfter,
            BigDecimal debitorFrozenBefore,
            BigDecimal debitorFicBefore,
            BigDecimal systemSourceBalBefore,
            BigDecimal systemSourceBalAfter,
            BigDecimal systemSourceFrozenBefore,
            BigDecimal systemSourceFicBefore,
            BigDecimal systemTargetBalBefore,
            BigDecimal systemTargetBalAfter,
            BigDecimal systemTargetFrozenBefore,
            BigDecimal systemTargetFicBefore,
            BigDecimal creditorBalBefore,
            BigDecimal creditorBalAfter,
            BigDecimal creditorFrozenBefore,
            BigDecimal creditorFicBefore
    ) {
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        for (TransactionDetails transactionDetail : transactionDetails) {
            transactionDetail.setTransferOn(now);
            transactionDetail.setTransferStatus(status);

            Long sequenceNumber = transactionDetail.getId().getTxnSequenceNumber();
            if (Long.valueOf(1L).equals(sequenceNumber)) {
                applyBalanceSnapshot(
                        transactionDetail,
                        debitorBalBefore,
                        debitorBalAfter,
                        debitorFrozenBefore,
                        debitorFrozenBefore,
                        debitorFicBefore,
                        debitorFicBefore
                );
            } else if (Long.valueOf(2L).equals(sequenceNumber)) {
                applyBalanceSnapshot(
                        transactionDetail,
                        systemSourceBalBefore,
                        systemSourceBalAfter,
                        systemSourceFrozenBefore,
                        systemSourceFrozenBefore,
                        systemSourceFicBefore,
                        systemSourceFicBefore
                );
            } else if (Long.valueOf(3L).equals(sequenceNumber)) {
                applyBalanceSnapshot(
                        transactionDetail,
                        systemTargetBalBefore,
                        systemTargetBalAfter,
                        systemTargetFrozenBefore,
                        systemTargetFrozenBefore,
                        systemTargetFicBefore,
                        systemTargetFicBefore
                );
            } else if (Long.valueOf(4L).equals(sequenceNumber)) {
                applyBalanceSnapshot(
                        transactionDetail,
                        creditorBalBefore,
                        creditorBalAfter,
                        creditorFrozenBefore,
                        creditorFrozenBefore,
                        creditorFicBefore,
                        creditorFicBefore
                );
            }
        }
        transactionDetailsRepository.saveAll(transactionDetails);
    }

    private void validateCurrencyExchangeTransactionDetails(String txnId) {
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        if (transactionDetails.size() != 4) {
            throw new ApplicationException(
                    ErrorCodes.SYSTEM_ERROR,
                    "Currency exchange intra-wallet transfer must have exactly four transaction detail entries"
            );
        }
    }

    private void applyBalanceSnapshot(
            TransactionDetails transactionDetail,
            BigDecimal previousBalance,
            BigDecimal postBalance,
            BigDecimal previousFrozenBalance,
            BigDecimal postFrozenBalance,
            BigDecimal previousFicBalance,
            BigDecimal postFicBalance
    ) {
        transactionDetail.setPreviousBalance(previousBalance);
        transactionDetail.setPostBalance(postBalance);
        transactionDetail.setPreviousFrozenBalance(previousFrozenBalance);
        transactionDetail.setPostFrozenBalance(postFrozenBalance);
        transactionDetail.setPreviousFicBalance(previousFicBalance);
        transactionDetail.setPostFicBalance(postFicBalance);
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

    private Map<Long, WalletBalance> lockBalances(Wallet... wallets) {
        TreeSet<Long> walletIds = new TreeSet<>();
        for (Wallet wallet : wallets) {
            walletIds.add(wallet.getWalletId());
        }

        Map<Long, WalletBalance> lockedBalances = new HashMap<>();
        for (Long walletId : walletIds) {
            lockedBalances.put(walletId, lockBalance(walletId));
        }
        return lockedBalances;
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
