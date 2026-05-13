package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.entity.CashbackPayout;
import com.paynest.payments.repository.CashbackPayoutRepository;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.payments.validation.WalletRestrictionValidator;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.users.dto.response.BalanceResponse;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BalanceService {

    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final String SERVICE_CHARGE_PAYER_SENDER = "SENDER";
    private static final String SERVICE_CHARGE_PAYER_RECEIVER = "RECEIVER";
    private static final String SERVICE_CHARGE_PAYER_SYSTEM = "SYSTEM";
    private static final String SERVICE_CHARGE_WALLET_TYPE = "SC";
    private static final String COMMISSION_WALLET_TYPE = "COMMDIS";
    private static final String MAIN_WALLET_TYPE = "MAIN";
    private static final String CASHBACK_SCHEDULE_IMMEDIATE = "IMMEDIATE";
    private static final String CASHBACK_SCHEDULE_END_OF_DAY = "END_OF_DAY";
    private static final String CASHBACK_SCHEDULE_END_OF_MONTH = "END_OF_MONTH";
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository balanceRepository;
    private final AccountRepository accountRepo;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final CashbackPayoutRepository cashbackPayoutRepository;
    private final PropertyReader propertyReader;
    private final WalletBalanceRepository balanceRepo;
    private final WalletLedgerRepository ledgerRepo;
    private final TransactionsService transactionsService;
    private final WalletCacheService walletCacheService;
    private final WalletRestrictionValidator walletRestrictionValidator;
    private final TransactionNotificationEventPublisher transactionNotificationEventPublisher;

    public BalanceService(WalletRepository walletRepository,
                          WalletBalanceRepository balanceRepository,
                          AccountRepository accountRepo,
                          TransactionsRepository transactionsRepository,
                          TransactionDetailsRepository transactionDetailsRepository,
                          CashbackPayoutRepository cashbackPayoutRepository,
                          PropertyReader propertyReader,
                          WalletBalanceRepository balanceRepo,
                          WalletLedgerRepository ledgerRepo,
                          TransactionsService transactionsService,
                          WalletCacheService walletCacheService,
                          WalletRestrictionValidator walletRestrictionValidator,
                          TransactionNotificationEventPublisher transactionNotificationEventPublisher) {
        this.walletRepository = walletRepository;
        this.balanceRepository = balanceRepository;
        this.accountRepo = accountRepo;
        this.transactionsRepository = transactionsRepository;
        this.transactionDetailsRepository = transactionDetailsRepository;
        this.cashbackPayoutRepository = cashbackPayoutRepository;
        this.propertyReader = propertyReader;
        this.balanceRepo = balanceRepo;
        this.ledgerRepo = ledgerRepo;
        this.transactionsService = transactionsService;
        this.walletCacheService = walletCacheService;
        this.walletRestrictionValidator = walletRestrictionValidator;
        this.transactionNotificationEventPublisher = transactionNotificationEventPublisher;
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

            Map<Long, WalletBalance> lockedBalances = lockBalancesInWalletIdOrder(
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
    public void transferWalletAmountWithServiceCharge(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            BigDecimal serviceChargeAmount,
            String serviceChargePayer,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId
    ) {
        transferWalletAmountWithServiceCharge(
                debitorWallet,
                creditorWallet,
                amount,
                serviceChargeAmount,
                serviceChargePayer,
                serviceCode,
                initiatedBy,
                txnId,
                null
        );
    }

    @Transactional
    public void transferWalletAmountWithServiceCharge(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            BigDecimal serviceChargeAmount,
            String serviceChargePayer,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId,
            PricingComputationResponse pricingComputation
    ) {
        if (serviceChargeAmount == null || serviceChargeAmount.compareTo(BigDecimal.ZERO) <= 0
                || serviceChargePayer == null || serviceChargePayer.isBlank()
                || SERVICE_CHARGE_PAYER_SYSTEM.equalsIgnoreCase(serviceChargePayer)) {
            transferWalletAmount(debitorWallet, creditorWallet, amount, serviceCode, initiatedBy, txnId);
            return;
        }

        try {
            String normalizedServiceChargePayer = serviceChargePayer.trim().toUpperCase();
            if (!SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    && !SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedServiceChargePayer)) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_REQUEST,
                        "Only SENDER and RECEIVER service charge payers are supported for wallet debit"
                );
            }

            Wallet serviceChargeWallet = SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedServiceChargePayer)
                    ? creditorWallet
                    : debitorWallet;
            Wallet systemWallet = getActiveSystemWallet(serviceChargeWallet);

            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
            LocalDateTime now = TenantTime.now();
            BigDecimal dbAmount = amount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal dbServiceChargeAmount = serviceChargeAmount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            Map<Long, WalletBalance> lockedBalances = lockBalancesInWalletIdOrder(
                    debitorWallet,
                    creditorWallet,
                    systemWallet
            );

            WalletBalance debitorBalance = lockedBalances.get(debitorWallet.getWalletId());
            WalletBalance creditorBalance = lockedBalances.get(creditorWallet.getWalletId());
            WalletBalance systemBalance = lockedBalances.get(systemWallet.getWalletId());

            BigDecimal senderBalBefore = debitorBalance.getAvailableBalance();
            BigDecimal senderFicBefore = debitorBalance.getFicBalance();
            BigDecimal senderFrozenBefore = debitorBalance.getFrozenBalance();

            BigDecimal receiverBalBefore = creditorBalance.getAvailableBalance();
            BigDecimal receiverFicBefore = creditorBalance.getFicBalance();
            BigDecimal receiverFrozenBefore = creditorBalance.getFrozenBalance();

            BigDecimal systemBalBefore = systemBalance.getAvailableBalance();
            BigDecimal systemFicBefore = systemBalance.getFicBalance();
            BigDecimal systemFrozenBefore = systemBalance.getFrozenBalance();

            BigDecimal senderServiceCharge = SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    ? dbServiceChargeAmount
                    : BigDecimal.ZERO;
            BigDecimal receiverServiceCharge = SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedServiceChargePayer)
                    ? dbServiceChargeAmount
                    : BigDecimal.ZERO;

            BigDecimal senderRequiredAmount = dbAmount.add(senderServiceCharge);
            BigDecimal senderNetBalance = senderBalBefore
                    .subtract(senderFicBefore)
                    .subtract(senderFrozenBefore);

            if (requiresBalanceCheck(debitorWallet) && senderNetBalance.compareTo(senderRequiredAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", senderRequiredAmount.toPlainString(),
                                "walletId", debitorWallet.getWalletId()
                        )
                );
            }

            BigDecimal receiverNetBalanceAfterCredit = receiverBalBefore
                    .subtract(receiverFicBefore)
                    .subtract(receiverFrozenBefore)
                    .add(dbAmount);

            if (requiresBalanceCheck(creditorWallet)
                    && receiverNetBalanceAfterCredit.compareTo(receiverServiceCharge) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", receiverServiceCharge.toPlainString(),
                                "walletId", creditorWallet.getWalletId()
                        )
                );
            }

            BigDecimal senderBalAfter = senderBalBefore
                    .subtract(dbAmount)
                    .subtract(senderServiceCharge);
            BigDecimal receiverBalAfter = receiverBalBefore
                    .add(dbAmount)
                    .subtract(receiverServiceCharge);
            BigDecimal systemBalAfter = systemBalBefore.add(dbServiceChargeAmount);
            BigDecimal senderTransferBalAfter = senderBalBefore.subtract(dbAmount);
            BigDecimal receiverTransferBalAfter = receiverBalBefore.add(dbAmount);
            BigDecimal serviceChargePayerBalBefore = SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    ? senderTransferBalAfter
                    : receiverTransferBalAfter;
            BigDecimal serviceChargePayerBalAfter = SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    ? senderBalAfter
                    : receiverBalAfter;

            BigDecimal senderFicAfter = senderFicBefore;
            BigDecimal senderFrozenAfter = senderFrozenBefore;

            BigDecimal receiverFicAfter = receiverFicBefore;
            BigDecimal receiverFrozenAfter = receiverFrozenBefore;
            BigDecimal systemFicAfter = systemFicBefore;
            BigDecimal systemFrozenAfter = systemFrozenBefore;

            saveLedgerEntry(
                    txnId,
                    debitorWallet,
                    Constants.TXN_TYPE_DR,
                    dbAmount,
                    senderBalBefore,
                    senderTransferBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    creditorWallet,
                    Constants.TXN_TYPE_CR,
                    dbAmount,
                    receiverBalBefore,
                    receiverTransferBalAfter,
                    serviceCode
            );

            saveServiceChargeLedgerEntries(
                    txnId,
                    serviceCode,
                    serviceChargeWallet,
                    systemWallet,
                    dbServiceChargeAmount,
                    serviceChargePayerBalBefore,
                    serviceChargePayerBalAfter,
                    systemBalBefore,
                    systemBalAfter
            );

            debitorBalance.setAvailableBalance(senderBalAfter);
            creditorBalance.setAvailableBalance(receiverBalAfter);
            systemBalance.setAvailableBalance(systemBalAfter);

            balanceRepo.save(debitorBalance);
            balanceRepo.save(creditorBalance);
            balanceRepo.save(systemBalance);

            Transactions transaction = transactionsRepository.findByTransactionId(txnId);
            if (transaction != null) {
                transaction.setTransferOn(now);
                transaction.setModifiedOn(now);
                transaction.setModifiedBy(resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet));
                transaction.setPreviousStatus(transaction.getTransferStatus());
                transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
                transaction.setTransactionValue(dbAmount.add(dbServiceChargeAmount));
                transaction.setFeesDetails(buildServiceChargeDetails(
                        serviceCode,
                        dbAmount,
                        amount,
                        dbServiceChargeAmount,
                        serviceChargeAmount,
                        normalizedServiceChargePayer,
                        serviceChargeWallet,
                        systemWallet,
                        pricingComputation
                ));
                transactionsRepository.save(transaction);
            }

            updateTransactionDetails(
                    txnId,
                    now,
                    Constants.TRANSACTION_SUCCESS,
                    senderBalBefore,
                    senderTransferBalAfter,
                    senderFrozenBefore,
                    senderFrozenAfter,
                    senderFicBefore,
                    senderFicAfter,
                    receiverBalBefore,
                    receiverTransferBalAfter,
                    receiverFrozenBefore,
                    receiverFrozenAfter,
                    receiverFicBefore,
                    receiverFicAfter
            );
            updatePrimaryTransactionDetailAmounts(
                    txnId,
                    dbAmount,
                    dbServiceChargeAmount,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
            walletCacheService.refreshAccountWallets(debitorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(systemWallet.getAccountId());
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
    public void transferWalletAmountWithPricing(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId,
            PricingComputationResponse pricingComputation
    ) {
        if (!hasPricingAdjustments(pricingComputation)) {
            transferWalletAmount(debitorWallet, creditorWallet, amount, serviceCode, initiatedBy, txnId);
            return;
        }

        BigDecimal discountAmount = positiveAmount(pricingComputation.getDiscountAmount());
        BigDecimal netAmount = resolveDiscountedAmount(amount, discountAmount);
        BigDecimal serviceChargeAmount = positiveAmount(pricingComputation.getServiceChargeAmount());
        BigDecimal commissionAmount = positiveAmount(pricingComputation.getCommissionAmount());
        String serviceChargePayer = pricingComputation.getServiceChargeAffectedParty();

        try {
            if (isWalletServiceCharge(serviceChargeAmount, serviceChargePayer)) {
                transferWalletAmountWithServiceCharge(
                        debitorWallet,
                        creditorWallet,
                        netAmount,
                        serviceChargeAmount,
                        serviceChargePayer,
                        serviceCode,
                        initiatedBy,
                        txnId,
                        pricingComputation
                );
            } else {
                transferWalletAmount(debitorWallet, creditorWallet, netAmount, serviceCode, initiatedBy, txnId);
            }

            Long nextSequenceNumber = 3L;
            PricingAdjustmentApplication commissionApplication = applyCommissionIfRequired(
                    debitorWallet,
                    creditorWallet,
                    serviceCode,
                    txnId,
                    pricingComputation,
                    Constants.TRANSACTION_SUCCESS,
                    nextSequenceNumber
            );
            nextSequenceNumber = nextSequenceNumber(commissionApplication, nextSequenceNumber);

            PricingAdjustmentApplication discountApplication = applyDiscountIfRequired(
                    debitorWallet,
                    creditorWallet,
                    serviceCode,
                    txnId,
                    pricingComputation,
                    Constants.TRANSACTION_SUCCESS,
                    nextSequenceNumber
            );
            nextSequenceNumber = nextSequenceNumber(discountApplication, nextSequenceNumber);

            CashbackApplication cashbackApplication = applyCashbackIfRequired(
                    debitorWallet,
                    creditorWallet,
                    serviceCode,
                    txnId,
                    pricingComputation,
                    Constants.TRANSACTION_SUCCESS,
                    nextSequenceNumber
            );

            updatePricingTransactionSummary(
                    txnId,
                    serviceCode,
                    amount,
                    netAmount,
                    serviceChargeAmount,
                    discountAmount,
                    commissionAmount,
                    positiveAmount(pricingComputation.getCashbackAmount()),
                    serviceChargePayer,
                    debitorWallet,
                    creditorWallet,
                    pricingComputation,
                    commissionApplication,
                    discountApplication,
                    cashbackApplication
            );
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

    @Transactional
    public void parkWalletAmountInFicWithPricing(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId,
            PricingComputationResponse pricingComputation
    ) {
        if (!hasPricingAdjustments(pricingComputation)) {
            parkWalletAmountInFic(debitorWallet, creditorWallet, amount, serviceCode, initiatedBy, txnId);
            return;
        }

        BigDecimal discountAmount = positiveAmount(pricingComputation.getDiscountAmount());
        BigDecimal netAmount = resolveDiscountedAmount(amount, discountAmount);
        BigDecimal serviceChargeAmount = positiveAmount(pricingComputation.getServiceChargeAmount());
        BigDecimal commissionAmount = positiveAmount(pricingComputation.getCommissionAmount());
        String serviceChargePayer = pricingComputation.getServiceChargeAffectedParty();

        try {
            if (isWalletServiceCharge(serviceChargeAmount, serviceChargePayer)) {
                parkWalletAmountInFicWithServiceCharge(
                        debitorWallet,
                        creditorWallet,
                        netAmount,
                        serviceChargeAmount,
                        serviceChargePayer,
                        serviceCode,
                        initiatedBy,
                        txnId,
                        pricingComputation
                );
            } else {
                parkWalletAmountInFic(debitorWallet, creditorWallet, netAmount, serviceCode, initiatedBy, txnId);
            }

            Long nextSequenceNumber = 3L;
            PricingAdjustmentApplication commissionApplication = applyCommissionIfRequired(
                    debitorWallet,
                    creditorWallet,
                    serviceCode,
                    txnId,
                    pricingComputation,
                    Constants.TRANSACTION_AMBIGUOUS,
                    nextSequenceNumber
            );
            nextSequenceNumber = nextSequenceNumber(commissionApplication, nextSequenceNumber);

            PricingAdjustmentApplication discountApplication = applyDiscountIfRequired(
                    debitorWallet,
                    creditorWallet,
                    serviceCode,
                    txnId,
                    pricingComputation,
                    Constants.TRANSACTION_AMBIGUOUS,
                    nextSequenceNumber
            );
            nextSequenceNumber = nextSequenceNumber(discountApplication, nextSequenceNumber);

            CashbackApplication cashbackApplication = applyCashbackIfRequired(
                    debitorWallet,
                    creditorWallet,
                    serviceCode,
                    txnId,
                    pricingComputation,
                    Constants.TRANSACTION_AMBIGUOUS,
                    nextSequenceNumber
            );

            updatePricingTransactionSummary(
                    txnId,
                    serviceCode,
                    amount,
                    netAmount,
                    serviceChargeAmount,
                    discountAmount,
                    commissionAmount,
                    positiveAmount(pricingComputation.getCashbackAmount()),
                    serviceChargePayer,
                    debitorWallet,
                    creditorWallet,
                    pricingComputation,
                    commissionApplication,
                    discountApplication,
                    cashbackApplication
            );
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
    public void parkWalletAmountInFicWithServiceCharge(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            BigDecimal serviceChargeAmount,
            String serviceChargePayer,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId
    ) {
        parkWalletAmountInFicWithServiceCharge(
                debitorWallet,
                creditorWallet,
                amount,
                serviceChargeAmount,
                serviceChargePayer,
                serviceCode,
                initiatedBy,
                txnId,
                null
        );
    }

    @Transactional
    public void parkWalletAmountInFicWithServiceCharge(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            BigDecimal serviceChargeAmount,
            String serviceChargePayer,
            String serviceCode,
            InitiatedBy initiatedBy,
            String txnId,
            PricingComputationResponse pricingComputation
    ) {
        if (serviceChargeAmount == null || serviceChargeAmount.compareTo(BigDecimal.ZERO) <= 0
                || serviceChargePayer == null || serviceChargePayer.isBlank()
                || SERVICE_CHARGE_PAYER_SYSTEM.equalsIgnoreCase(serviceChargePayer)) {
            parkWalletAmountInFic(debitorWallet, creditorWallet, amount, serviceCode, initiatedBy, txnId);
            return;
        }

        try {
            String normalizedServiceChargePayer = serviceChargePayer.trim().toUpperCase();
            if (!SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    && !SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedServiceChargePayer)) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_REQUEST,
                        "Only SENDER and RECEIVER service charge payers are supported for wallet debit"
                );
            }

            Wallet serviceChargeWallet = SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedServiceChargePayer)
                    ? creditorWallet
                    : debitorWallet;
            Wallet systemWallet = getActiveSystemWallet(serviceChargeWallet);

            BigDecimal currencyFactor =
                    new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
            LocalDateTime now = TenantTime.now();
            BigDecimal dbAmount = amount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal dbServiceChargeAmount = serviceChargeAmount
                    .multiply(currencyFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            Map<Long, WalletBalance> lockedBalances = lockBalancesInWalletIdOrder(
                    debitorWallet,
                    creditorWallet,
                    systemWallet
            );

            WalletBalance debitorBalance = lockedBalances.get(debitorWallet.getWalletId());
            WalletBalance creditorBalance = lockedBalances.get(creditorWallet.getWalletId());
            WalletBalance systemBalance = lockedBalances.get(systemWallet.getWalletId());

            BigDecimal senderBalBefore = debitorBalance.getAvailableBalance();
            BigDecimal senderFicBefore = debitorBalance.getFicBalance();
            BigDecimal senderFrozenBefore = debitorBalance.getFrozenBalance();

            BigDecimal receiverBalBefore = creditorBalance.getAvailableBalance();
            BigDecimal receiverFicBefore = creditorBalance.getFicBalance();
            BigDecimal receiverFrozenBefore = creditorBalance.getFrozenBalance();

            BigDecimal systemBalBefore = systemBalance.getAvailableBalance();
            BigDecimal systemFicBefore = systemBalance.getFicBalance();
            BigDecimal systemFrozenBefore = systemBalance.getFrozenBalance();

            BigDecimal senderServiceCharge = SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    ? dbServiceChargeAmount
                    : BigDecimal.ZERO;
            BigDecimal receiverServiceCharge = SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedServiceChargePayer)
                    ? dbServiceChargeAmount
                    : BigDecimal.ZERO;

            BigDecimal senderRequiredAmount = dbAmount.add(senderServiceCharge);
            BigDecimal senderNetBalance = senderBalBefore
                    .subtract(senderFicBefore)
                    .subtract(senderFrozenBefore);

            if (senderNetBalance.compareTo(senderRequiredAmount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", senderRequiredAmount.toPlainString(),
                                "walletId", debitorWallet.getWalletId()
                        )
                );
            }

            BigDecimal receiverNetBalance = receiverBalBefore
                    .subtract(receiverFicBefore)
                    .subtract(receiverFrozenBefore);

            if (requiresBalanceCheck(creditorWallet) && receiverNetBalance.compareTo(receiverServiceCharge) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_BALANCE,
                        null,
                        txnId,
                        Map.of(
                                "amount", receiverServiceCharge.toPlainString(),
                                "walletId", creditorWallet.getWalletId()
                        )
                );
            }

            BigDecimal senderBalAfter = senderBalBefore
                    .subtract(dbAmount)
                    .subtract(senderServiceCharge);
            BigDecimal receiverBalAfter = receiverBalBefore
                    .add(dbAmount)
                    .subtract(receiverServiceCharge);
            BigDecimal systemBalAfter = systemBalBefore.add(dbServiceChargeAmount);
            BigDecimal senderTransferBalAfter = senderBalBefore.subtract(dbAmount);
            BigDecimal receiverTransferBalAfter = receiverBalBefore.add(dbAmount);
            BigDecimal serviceChargePayerBalBefore = SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    ? senderTransferBalAfter
                    : receiverTransferBalAfter;
            BigDecimal serviceChargePayerBalAfter = SERVICE_CHARGE_PAYER_SENDER.equals(normalizedServiceChargePayer)
                    ? senderBalAfter
                    : receiverBalAfter;

            BigDecimal senderFicAfter = senderFicBefore;
            BigDecimal senderFrozenAfter = senderFrozenBefore;
            BigDecimal receiverFicAfter = receiverFicBefore.add(dbAmount);
            BigDecimal receiverFrozenAfter = receiverFrozenBefore;
            BigDecimal systemFicAfter = systemFicBefore;
            BigDecimal systemFrozenAfter = systemFrozenBefore;

            saveLedgerEntry(
                    txnId,
                    debitorWallet,
                    Constants.TXN_TYPE_DR,
                    dbAmount,
                    senderBalBefore,
                    senderTransferBalAfter,
                    serviceCode
            );
            saveLedgerEntry(
                    txnId,
                    creditorWallet,
                    Constants.TXN_TYPE_CR,
                    dbAmount,
                    receiverBalBefore,
                    receiverTransferBalAfter,
                    serviceCode
            );

            saveServiceChargeLedgerEntries(
                    txnId,
                    serviceCode,
                    serviceChargeWallet,
                    systemWallet,
                    dbServiceChargeAmount,
                    serviceChargePayerBalBefore,
                    serviceChargePayerBalAfter,
                    systemBalBefore,
                    systemBalAfter
            );

            debitorBalance.setAvailableBalance(senderBalAfter);
            creditorBalance.setAvailableBalance(receiverBalAfter);
            creditorBalance.setFicBalance(receiverFicAfter);
            systemBalance.setAvailableBalance(systemBalAfter);

            balanceRepo.save(debitorBalance);
            balanceRepo.save(creditorBalance);
            balanceRepo.save(systemBalance);

            Transactions transaction = transactionsRepository.findByTransactionId(txnId);
            if (transaction != null) {
                transaction.setTransferOn(now);
                transaction.setModifiedOn(now);
                transaction.setModifiedBy(resolveActorAccountId(initiatedBy, debitorWallet, creditorWallet));
                transaction.setPreviousStatus(transaction.getTransferStatus());
                transaction.setTransferStatus(Constants.TRANSACTION_AMBIGUOUS);
                transaction.setTransactionValue(dbAmount.add(dbServiceChargeAmount));
                transaction.setFeesDetails(buildServiceChargeDetails(
                        serviceCode,
                        dbAmount,
                        amount,
                        dbServiceChargeAmount,
                        serviceChargeAmount,
                        normalizedServiceChargePayer,
                        serviceChargeWallet,
                        systemWallet,
                        pricingComputation
                ));
                transactionsRepository.save(transaction);
            }

            updateTransactionDetails(
                    txnId,
                    now,
                    Constants.TRANSACTION_AMBIGUOUS,
                    senderBalBefore,
                    senderTransferBalAfter,
                    senderFrozenBefore,
                    senderFrozenAfter,
                    senderFicBefore,
                    senderFicAfter,
                    receiverBalBefore,
                    receiverTransferBalAfter,
                    receiverFrozenBefore,
                    receiverFrozenAfter,
                    receiverFicBefore,
                    receiverFicAfter
            );
            updatePrimaryTransactionDetailAmounts(
                    txnId,
                    dbAmount,
                    dbServiceChargeAmount,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
            walletCacheService.refreshAccountWallets(debitorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
            walletCacheService.refreshAccountWallets(systemWallet.getAccountId());
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
        saveLedgerEntry(txnId, wallet, entryType, amount, balanceBefore, balanceAfter, serviceCode, null);
    }

    private void saveLedgerEntry(
            String txnId,
            Wallet wallet,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String serviceCode,
            String description
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

    private void saveServiceChargeLedgerEntries(
            String txnId,
            String serviceCode,
            Wallet serviceChargeWallet,
            Wallet systemWallet,
            BigDecimal dbServiceChargeAmount,
            BigDecimal payerBalanceBefore,
            BigDecimal payerBalanceAfter,
            BigDecimal systemBalanceBefore,
            BigDecimal systemBalanceAfter
    ) {
        String debitDescription = "Service charge debit; payerAccountId=%s; payerWalletId=%d; creditedAccountId=%s; creditedWalletId=%d"
                .formatted(
                        serviceChargeWallet.getAccountId(),
                        serviceChargeWallet.getWalletId(),
                        systemWallet.getAccountId(),
                        systemWallet.getWalletId()
                );
        String creditDescription = "Service charge credit; sourceAccountId=%s; sourceWalletId=%d; creditedAccountId=%s; creditedWalletId=%d"
                .formatted(
                        serviceChargeWallet.getAccountId(),
                        serviceChargeWallet.getWalletId(),
                        systemWallet.getAccountId(),
                        systemWallet.getWalletId()
                );
        saveLedgerEntry(
                txnId,
                serviceChargeWallet,
                Constants.TXN_TYPE_DR,
                dbServiceChargeAmount,
                payerBalanceBefore,
                payerBalanceAfter,
                serviceCode,
                debitDescription
        );
        saveLedgerEntry(
                txnId,
                systemWallet,
                Constants.TXN_TYPE_CR,
                dbServiceChargeAmount,
                systemBalanceBefore,
                systemBalanceAfter,
                serviceCode,
                creditDescription
        );
    }

    private void saveServiceChargeTransactionDetails(
            String txnId,
            LocalDateTime now,
            String status,
            String serviceCode,
            Wallet serviceChargeWallet,
            Wallet systemWallet,
            BigDecimal dbServiceChargeAmount,
            BigDecimal payerBalanceBefore,
            BigDecimal payerBalanceAfter,
            BigDecimal payerFrozenBefore,
            BigDecimal payerFrozenAfter,
            BigDecimal payerFicBefore,
            BigDecimal payerFicAfter,
            BigDecimal systemBalanceBefore,
            BigDecimal systemBalanceAfter,
            BigDecimal systemFrozenBefore,
            BigDecimal systemFrozenAfter,
            BigDecimal systemFicBefore,
            BigDecimal systemFicAfter
    ) {
        List<TransactionDetails> existingDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        TransactionDetails payerDetail = findDetailForAccount(existingDetails, serviceChargeWallet.getAccountId());
        String payerIdentifier = payerDetail == null
                ? serviceChargeWallet.getAccountId()
                : payerDetail.getIdentifierId();
        String payerUserType = payerDetail == null
                ? "UNKNOWN"
                : payerDetail.getUserType();
        String systemIdentifier = systemWallet.getWalletId().toString();

        TransactionDetails debitDetail = buildServiceChargeTransactionDetail(
                txnId,
                3L,
                serviceChargeWallet,
                Constants.TXN_TYPE_DR,
                payerUserType,
                payerIdentifier,
                systemIdentifier,
                dbServiceChargeAmount,
                payerBalanceBefore,
                payerBalanceAfter,
                payerFrozenBefore,
                payerFrozenAfter,
                payerFicBefore,
                payerFicAfter,
                now,
                serviceCode,
                status
        );

        TransactionDetails creditDetail = buildServiceChargeTransactionDetail(
                txnId,
                4L,
                systemWallet,
                Constants.TXN_TYPE_CR,
                "SYSTEM",
                systemIdentifier,
                payerIdentifier,
                dbServiceChargeAmount,
                systemBalanceBefore,
                systemBalanceAfter,
                systemFrozenBefore,
                systemFrozenAfter,
                systemFicBefore,
                systemFicAfter,
                now,
                serviceCode,
                status
        );

        transactionDetailsRepository.saveAll(List.of(debitDetail, creditDetail));
    }

    private TransactionDetails buildServiceChargeTransactionDetail(
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
            BigDecimal frozenBalanceBefore,
            BigDecimal frozenBalanceAfter,
            BigDecimal ficBalanceBefore,
            BigDecimal ficBalanceAfter,
            LocalDateTime now,
            String serviceCode,
            String status
    ) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(txnId, sequenceNumber));
        detail.setAccountId(wallet.getAccountId());
        detail.setUserType(defaultIfBlank(userType, "UNKNOWN"));
        detail.setEntryType(entryType);
        detail.setIdentifierId(defaultIfBlank(identifierId, wallet.getAccountId()));
        detail.setSecondIdentifierId(limitSecondIdentifier(defaultIfBlank(secondIdentifierId, wallet.getWalletId().toString())));
        detail.setTransactionValue(amount);
        detail.setApprovedValue(amount);
        detail.setPreviousBalance(balanceBefore);
        detail.setPostBalance(balanceAfter);
        detail.setPreviousFrozenBalance(frozenBalanceBefore);
        detail.setPostFrozenBalance(frozenBalanceAfter);
        detail.setPreviousFicBalance(ficBalanceBefore);
        detail.setPostFicBalance(ficBalanceAfter);
        detail.setTransferOn(now);
        detail.setServiceCode(serviceCode);
        detail.setTransferStatus(status);
        detail.setWalletNumber(wallet.getWalletId().toString());
        detail.setWalletType(wallet.getWalletType());
        detail.setCurrency(wallet.getCurrency());
        detail.setTransactionType(Constants.TXN_TYPE_DR.equalsIgnoreCase(entryType)
                ? Constants.TXN_DETAIL_TYPE_SERVICE_CHARGE_PAID
                : Constants.TXN_DETAIL_TYPE_SERVICE_CHARGE_RECEIVED);
        setPricingDetailAttributes(detail, "service_charge", amount);
        return detail;
    }

    private TransactionDetails findDetailForAccount(List<TransactionDetails> details, String accountId) {
        return details.stream()
                .filter(detail -> accountId.equalsIgnoreCase(detail.getAccountId()))
                .filter(detail -> detail.getId() != null && detail.getId().getTxnSequenceNumber() <= 2)
                .findFirst()
                .orElse(null);
    }

    private boolean hasPricingAdjustments(PricingComputationResponse pricingComputation) {
        if (pricingComputation == null) {
            return false;
        }
        return positiveAmount(pricingComputation.getServiceChargeAmount()).compareTo(BigDecimal.ZERO) > 0
                || positiveAmount(pricingComputation.getCommissionAmount()).compareTo(BigDecimal.ZERO) > 0
                || positiveAmount(pricingComputation.getDiscountAmount()).compareTo(BigDecimal.ZERO) > 0
                || positiveAmount(pricingComputation.getCashbackAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amount;
    }

    private BigDecimal resolveDiscountedAmount(BigDecimal amount, BigDecimal discountAmount) {
        BigDecimal netAmount = amount.subtract(discountAmount);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "Discount amount cannot exceed transaction amount"
            );
        }
        return netAmount;
    }

    private boolean isWalletServiceCharge(BigDecimal serviceChargeAmount, String serviceChargePayer) {
        if (serviceChargeAmount == null || serviceChargeAmount.compareTo(BigDecimal.ZERO) <= 0
                || serviceChargePayer == null || serviceChargePayer.isBlank()) {
            return false;
        }
        String normalizedPayer = serviceChargePayer.trim().toUpperCase();
        return SERVICE_CHARGE_PAYER_SENDER.equals(normalizedPayer)
                || SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedPayer);
    }

    private String normalizeWalletParty(String party, String fieldName) {
        if (party == null || party.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, fieldName + " is required");
        }
        String normalizedParty = party.trim().toUpperCase();
        if (!SERVICE_CHARGE_PAYER_SENDER.equals(normalizedParty)
                && !SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedParty)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    fieldName + " must be SENDER or RECEIVER"
            );
        }
        return normalizedParty;
    }

    private String normalizeCashbackPayBy(String payBy) {
        if (payBy == null || payBy.isBlank()) {
            return SERVICE_CHARGE_PAYER_SYSTEM;
        }
        String normalizedPayBy = payBy.trim().toUpperCase();
        if (!SERVICE_CHARGE_PAYER_SYSTEM.equals(normalizedPayBy)
                && !SERVICE_CHARGE_PAYER_SENDER.equals(normalizedPayBy)
                && !SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedPayBy)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "cashback payBy must be SYSTEM, SENDER, or RECEIVER"
            );
        }
        return normalizedPayBy;
    }

    private PricingAdjustmentApplication applyCommissionIfRequired(
            Wallet debitorWallet,
            Wallet creditorWallet,
            String serviceCode,
            String txnId,
            PricingComputationResponse pricingComputation,
            String status,
            Long firstSequenceNumber
    ) {
        BigDecimal commissionAmount = pricingComputation == null
                ? BigDecimal.ZERO
                : positiveAmount(pricingComputation.getCommissionAmount());
        if (commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return PricingAdjustmentApplication.none();
        }

        String beneficiaryParty = normalizeWalletParty(
                pricingComputation.getCommissionAffectedParty(),
                "commission beneficiary"
        );
        Wallet partyWallet = SERVICE_CHARGE_PAYER_RECEIVER.equals(beneficiaryParty)
                ? creditorWallet
                : debitorWallet;
        Wallet sourceWallet = getActiveSystemWallet(partyWallet, COMMISSION_WALLET_TYPE, "commission");
        Wallet beneficiaryWallet = findActiveAccountWallet(
                partyWallet.getAccountId(),
                partyWallet.getCurrency(),
                COMMISSION_WALLET_TYPE
        ).orElseGet(() -> getActiveAccountWallet(
                partyWallet.getAccountId(),
                partyWallet.getCurrency(),
                MAIN_WALLET_TYPE,
                "commission beneficiary"
        ));

        return applyFundedPricingAdjustment(
                "commission",
                debitorWallet,
                creditorWallet,
                serviceCode,
                txnId,
                commissionAmount,
                sourceWallet,
                beneficiaryWallet,
                beneficiaryParty,
                status,
                firstSequenceNumber
        );
    }

    private PricingAdjustmentApplication applyDiscountIfRequired(
            Wallet debitorWallet,
            Wallet creditorWallet,
            String serviceCode,
            String txnId,
            PricingComputationResponse pricingComputation,
            String status,
            Long firstSequenceNumber
    ) {
        BigDecimal discountAmount = pricingComputation == null
                ? BigDecimal.ZERO
                : positiveAmount(pricingComputation.getDiscountAmount());
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return PricingAdjustmentApplication.none();
        }

        String beneficiaryParty = normalizeWalletParty(
                pricingComputation.getDiscountAffectedParty(),
                "discount beneficiary"
        );
        Wallet partyWallet = SERVICE_CHARGE_PAYER_RECEIVER.equals(beneficiaryParty)
                ? creditorWallet
                : debitorWallet;
        Wallet sourceWallet = getActiveSystemWallet(partyWallet, COMMISSION_WALLET_TYPE, "discount");
        Wallet beneficiaryWallet = getActiveAccountWallet(
                partyWallet.getAccountId(),
                partyWallet.getCurrency(),
                MAIN_WALLET_TYPE,
                "discount beneficiary"
        );

        return applyFundedPricingAdjustment(
                "discount",
                debitorWallet,
                creditorWallet,
                serviceCode,
                txnId,
                discountAmount,
                sourceWallet,
                beneficiaryWallet,
                beneficiaryParty,
                status,
                firstSequenceNumber
        );
    }

    private PricingAdjustmentApplication applyFundedPricingAdjustment(
            String adjustmentType,
            Wallet debitorWallet,
            Wallet creditorWallet,
            String serviceCode,
            String txnId,
            BigDecimal adjustmentAmount,
            Wallet sourceWallet,
            Wallet beneficiaryWallet,
            String beneficiaryParty,
            String status,
            Long firstSequenceNumber
    ) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        LocalDateTime now = TenantTime.now();
        BigDecimal dbAdjustmentAmount = adjustmentAmount
                .multiply(currencyFactor)
                .setScale(2, RoundingMode.HALF_UP);

        Map<Long, WalletBalance> lockedBalances = lockBalancesInWalletIdOrder(sourceWallet, beneficiaryWallet);
        WalletBalance sourceBalance = lockedBalances.get(sourceWallet.getWalletId());
        WalletBalance beneficiaryBalance = lockedBalances.get(beneficiaryWallet.getWalletId());

        BigDecimal sourceBalBefore = sourceBalance.getAvailableBalance();
        BigDecimal sourceFicBefore = sourceBalance.getFicBalance();
        BigDecimal sourceFrozenBefore = sourceBalance.getFrozenBalance();
        BigDecimal sourceNetBalance = sourceBalBefore
                .subtract(sourceFicBefore)
                .subtract(sourceFrozenBefore);

        if (requiresBalanceCheck(sourceWallet) && sourceNetBalance.compareTo(dbAdjustmentAmount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_BALANCE,
                    null,
                    txnId,
                    Map.of(
                            "amount", dbAdjustmentAmount.toPlainString(),
                            "walletId", sourceWallet.getWalletId()
                    )
            );
        }

        BigDecimal beneficiaryBalBefore = beneficiaryBalance.getAvailableBalance();
        BigDecimal beneficiaryFicBefore = beneficiaryBalance.getFicBalance();
        BigDecimal beneficiaryFrozenBefore = beneficiaryBalance.getFrozenBalance();

        BigDecimal sourceBalAfter = sourceBalBefore.subtract(dbAdjustmentAmount);
        BigDecimal beneficiaryBalAfter = beneficiaryBalBefore.add(dbAdjustmentAmount);

        savePricingAdjustmentLedgerEntries(
                adjustmentType,
                txnId,
                serviceCode,
                sourceWallet,
                beneficiaryWallet,
                dbAdjustmentAmount,
                sourceBalBefore,
                sourceBalAfter,
                beneficiaryBalBefore,
                beneficiaryBalAfter
        );

        sourceBalance.setAvailableBalance(sourceBalAfter);
        beneficiaryBalance.setAvailableBalance(beneficiaryBalAfter);

        balanceRepo.save(sourceBalance);
        balanceRepo.save(beneficiaryBalance);

        savePricingAdjustmentTransactionDetails(
                adjustmentType,
                txnId,
                now,
                serviceCode,
                sourceWallet,
                beneficiaryWallet,
                dbAdjustmentAmount,
                sourceBalBefore,
                sourceBalAfter,
                sourceFrozenBefore,
                sourceFrozenBefore,
                sourceFicBefore,
                sourceFicBefore,
                beneficiaryBalBefore,
                beneficiaryBalAfter,
                beneficiaryFrozenBefore,
                beneficiaryFrozenBefore,
                beneficiaryFicBefore,
                beneficiaryFicBefore,
                status,
                firstSequenceNumber
        );

        walletCacheService.refreshAccountWallets(sourceWallet.getAccountId());
        walletCacheService.refreshAccountWallets(beneficiaryWallet.getAccountId());

        return new PricingAdjustmentApplication(
                true,
                adjustmentType,
                sourceWallet,
                beneficiaryWallet,
                dbAdjustmentAmount,
                adjustmentAmount,
                beneficiaryParty,
                firstSequenceNumber,
                firstSequenceNumber + 1
        );
    }

    private Long nextSequenceNumber(PricingAdjustmentApplication application, Long currentSequenceNumber) {
        if (application == null || !application.applied) {
            return currentSequenceNumber;
        }
        return application.creditSequenceNumber + 1;
    }

    private CashbackApplication applyCashbackIfRequired(
            Wallet debitorWallet,
            Wallet creditorWallet,
            String serviceCode,
            String txnId,
            PricingComputationResponse pricingComputation,
            String status,
            Long firstSequenceNumber
    ) {
        BigDecimal cashbackAmount = pricingComputation == null
                ? BigDecimal.ZERO
                : positiveAmount(pricingComputation.getCashbackAmount());
        if (cashbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return CashbackApplication.none();
        }

        String beneficiaryParty = normalizeWalletParty(pricingComputation.getCashbackAffectedParty(), "cashback beneficiary");
        Wallet beneficiaryWallet = SERVICE_CHARGE_PAYER_RECEIVER.equals(beneficiaryParty)
                ? creditorWallet
                : debitorWallet;

        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        LocalDateTime now = TenantTime.now();
        BigDecimal dbCashbackAmount = cashbackAmount
                .multiply(currencyFactor)
                .setScale(2, RoundingMode.HALF_UP);
        String paymentSchedule = resolveCashbackPaymentSchedule(pricingComputation);
        LocalDateTime payAt = resolveCashbackPayAt(paymentSchedule, now);

        CashbackPayout payout = new CashbackPayout();
        payout.setOriginalTransactionId(txnId);
        payout.setServiceCode(serviceCode);
        payout.setBeneficiaryAccountId(beneficiaryWallet.getAccountId());
        payout.setBeneficiaryParty(beneficiaryParty);
        payout.setAmount(dbCashbackAmount);
        payout.setCurrency(trimCurrency(beneficiaryWallet.getCurrency()));
        payout.setPaymentSchedule(paymentSchedule);
        payout.setPayAt(payAt);
        payout.setStatus(CashbackPayoutService.STATUS_PENDING);
        payout.setPricingRuleDetails(buildPricingRules(
                pricingComputation == null ? null : pricingComputation.getCashbackRules()
        ).toString());
        cashbackPayoutRepository.save(payout);

        return new CashbackApplication(
                true,
                beneficiaryWallet,
                dbCashbackAmount,
                cashbackAmount,
                SERVICE_CHARGE_PAYER_SYSTEM,
                beneficiaryParty,
                paymentSchedule,
                payAt,
                payout.getCashbackPayoutId()
        );
    }

    private String resolveCashbackPaymentSchedule(PricingComputationResponse pricingComputation) {
        if (pricingComputation == null || pricingComputation.getCashbackRules() == null) {
            return CASHBACK_SCHEDULE_IMMEDIATE;
        }
        return pricingComputation.getCashbackRules().stream()
                .map(PricingComputationResponse.PricingRuleDetails::getPricingConfig)
                .map(this::extractCashbackPaymentSchedule)
                .filter(schedule -> schedule != null && !schedule.isBlank())
                .findFirst()
                .orElse(CASHBACK_SCHEDULE_IMMEDIATE);
    }

    private String extractCashbackPaymentSchedule(com.fasterxml.jackson.databind.JsonNode pricingConfig) {
        if (pricingConfig == null || pricingConfig.isNull()) {
            return null;
        }

        for (String fieldName : List.of("cashbackPayout", "cashback_payout", "payout", "payment")) {
            com.fasterxml.jackson.databind.JsonNode nested = pricingConfig.get(fieldName);
            String nestedSchedule = extractCashbackPaymentSchedule(nested);
            if (nestedSchedule != null) {
                return nestedSchedule;
            }
        }

        for (String fieldName : List.of("paymentSchedule", "payment_schedule", "payoutSchedule", "payout_schedule", "payAt", "pay_at")) {
            com.fasterxml.jackson.databind.JsonNode value = pricingConfig.get(fieldName);
            if (value != null && value.isTextual()) {
                return normalizeCashbackPaymentSchedule(value.asText());
            }
        }

        return null;
    }

    private String normalizeCashbackPaymentSchedule(String paymentSchedule) {
        if (paymentSchedule == null || paymentSchedule.isBlank()) {
            return CASHBACK_SCHEDULE_IMMEDIATE;
        }

        String normalizedSchedule = paymentSchedule.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalizedSchedule) {
            case "NOW", "INSTANT", "IMMEDIATE" -> CASHBACK_SCHEDULE_IMMEDIATE;
            case "EOD", "END_OF_DAY", "END_OF_TODAY" -> CASHBACK_SCHEDULE_END_OF_DAY;
            case "EOM", "END_OF_MONTH", "MONTH_END" -> CASHBACK_SCHEDULE_END_OF_MONTH;
            default -> CASHBACK_SCHEDULE_IMMEDIATE;
        };
    }

    private LocalDateTime resolveCashbackPayAt(String paymentSchedule, LocalDateTime now) {
        String normalizedSchedule = normalizeCashbackPaymentSchedule(paymentSchedule);
        return switch (normalizedSchedule) {
            case CASHBACK_SCHEDULE_END_OF_DAY -> now.toLocalDate().atTime(23, 59, 59);
            case CASHBACK_SCHEDULE_END_OF_MONTH -> now.toLocalDate()
                    .withDayOfMonth(now.toLocalDate().lengthOfMonth())
                    .atTime(23, 59, 59);
            default -> now;
        };
    }

    private void savePricingAdjustmentLedgerEntries(
            String adjustmentType,
            String txnId,
            String serviceCode,
            Wallet sourceWallet,
            Wallet beneficiaryWallet,
            BigDecimal dbAdjustmentAmount,
            BigDecimal sourceBalanceBefore,
            BigDecimal sourceBalanceAfter,
            BigDecimal beneficiaryBalanceBefore,
            BigDecimal beneficiaryBalanceAfter
    ) {
        String displayType = adjustmentType == null || adjustmentType.isBlank()
                ? "Pricing adjustment"
                : adjustmentType.substring(0, 1).toUpperCase() + adjustmentType.substring(1).toLowerCase();
        String debitDescription = "%s debit; sourceAccountId=%s; sourceWalletId=%d; beneficiaryAccountId=%s; beneficiaryWalletId=%d"
                .formatted(
                        displayType,
                        sourceWallet.getAccountId(),
                        sourceWallet.getWalletId(),
                        beneficiaryWallet.getAccountId(),
                        beneficiaryWallet.getWalletId()
                );
        String creditDescription = "%s credit; sourceAccountId=%s; sourceWalletId=%d; beneficiaryAccountId=%s; beneficiaryWalletId=%d"
                .formatted(
                        displayType,
                        sourceWallet.getAccountId(),
                        sourceWallet.getWalletId(),
                        beneficiaryWallet.getAccountId(),
                        beneficiaryWallet.getWalletId()
                );
        saveLedgerEntry(
                txnId,
                sourceWallet,
                Constants.TXN_TYPE_DR,
                dbAdjustmentAmount,
                sourceBalanceBefore,
                sourceBalanceAfter,
                serviceCode,
                debitDescription
        );
        saveLedgerEntry(
                txnId,
                beneficiaryWallet,
                Constants.TXN_TYPE_CR,
                dbAdjustmentAmount,
                beneficiaryBalanceBefore,
                beneficiaryBalanceAfter,
                serviceCode,
                creditDescription
        );
    }

    private void savePricingAdjustmentTransactionDetails(
            String adjustmentType,
            String txnId,
            LocalDateTime now,
            String serviceCode,
            Wallet sourceWallet,
            Wallet beneficiaryWallet,
            BigDecimal dbAdjustmentAmount,
            BigDecimal sourceBalanceBefore,
            BigDecimal sourceBalanceAfter,
            BigDecimal sourceFrozenBefore,
            BigDecimal sourceFrozenAfter,
            BigDecimal sourceFicBefore,
            BigDecimal sourceFicAfter,
            BigDecimal beneficiaryBalanceBefore,
            BigDecimal beneficiaryBalanceAfter,
            BigDecimal beneficiaryFrozenBefore,
            BigDecimal beneficiaryFrozenAfter,
            BigDecimal beneficiaryFicBefore,
            BigDecimal beneficiaryFicAfter,
            String status,
            Long firstSequenceNumber
    ) {
        List<TransactionDetails> existingDetails = transactionDetailsRepository.findByIdTransactionId(txnId);
        TransactionDetails beneficiaryDetail = findDetailForAccount(existingDetails, beneficiaryWallet.getAccountId());
        String beneficiaryIdentifier = beneficiaryDetail == null
                ? beneficiaryWallet.getAccountId()
                : beneficiaryDetail.getIdentifierId();
        String beneficiaryUserType = beneficiaryDetail == null
                ? "UNKNOWN"
                : beneficiaryDetail.getUserType();
        String systemIdentifier = sourceWallet.getWalletId().toString();

        TransactionDetails debitDetail = buildServiceChargeTransactionDetail(
                txnId,
                firstSequenceNumber,
                sourceWallet,
                Constants.TXN_TYPE_DR,
                "SYSTEM",
                systemIdentifier,
                beneficiaryIdentifier,
                dbAdjustmentAmount,
                sourceBalanceBefore,
                sourceBalanceAfter,
                sourceFrozenBefore,
                sourceFrozenAfter,
                sourceFicBefore,
                sourceFicAfter,
                now,
                serviceCode,
                status
        );

        TransactionDetails creditDetail = buildServiceChargeTransactionDetail(
                txnId,
                firstSequenceNumber + 1,
                beneficiaryWallet,
                Constants.TXN_TYPE_CR,
                beneficiaryUserType,
                beneficiaryIdentifier,
                systemIdentifier,
                dbAdjustmentAmount,
                beneficiaryBalanceBefore,
                beneficiaryBalanceAfter,
                beneficiaryFrozenBefore,
                beneficiaryFrozenAfter,
                beneficiaryFicBefore,
                beneficiaryFicAfter,
                now,
                serviceCode,
                status
        );

        setPricingAdjustmentMetadata(debitDetail, adjustmentType, dbAdjustmentAmount, true);
        setPricingAdjustmentMetadata(creditDetail, adjustmentType, dbAdjustmentAmount, false);

        transactionDetailsRepository.saveAll(List.of(debitDetail, creditDetail));
    }

    private void setPricingAdjustmentMetadata(
            TransactionDetails detail,
            String adjustmentType,
            BigDecimal amount,
            boolean debit
    ) {
        String normalizedAdjustmentType = adjustmentType == null ? "" : adjustmentType.trim().toLowerCase();
        if ("commission".equals(normalizedAdjustmentType)) {
            detail.setTransactionType(debit
                    ? Constants.TXN_DETAIL_TYPE_COMMISSION_PAID
                    : Constants.TXN_DETAIL_TYPE_COMMISSION_RECEIVED);
        } else if ("discount".equals(normalizedAdjustmentType)) {
            detail.setTransactionType(debit
                    ? Constants.TXN_DETAIL_TYPE_DISCOUNT_PAID
                    : Constants.TXN_DETAIL_TYPE_DISCOUNT_RECEIVED);
        }
        setPricingDetailAttributes(detail, normalizedAdjustmentType, amount);
    }

    private void setPricingDetailAttributes(TransactionDetails detail, String adjustmentType, BigDecimal amount) {
        detail.setAttr6Name(resolvePricingDetailName(adjustmentType));
        detail.setAttr6Value(amount == null ? null : amount.toPlainString());
    }

    private String resolvePricingDetailName(String adjustmentType) {
        String normalizedAdjustmentType = adjustmentType == null ? "" : adjustmentType.trim().toLowerCase();
        if ("commission".equals(normalizedAdjustmentType)) {
            return "COMMISSION";
        }
        if ("discount".equals(normalizedAdjustmentType)) {
            return "DISCOUNT";
        }
        return "SERVICE_CHARGE";
    }

    private void updatePricingTransactionSummary(
            String txnId,
            String serviceCode,
            BigDecimal originalAmount,
            BigDecimal netAmount,
            BigDecimal serviceChargeAmount,
            BigDecimal discountAmount,
            BigDecimal commissionAmount,
            BigDecimal cashbackAmount,
            String serviceChargePayer,
            Wallet debitorWallet,
            Wallet creditorWallet,
            PricingComputationResponse pricingComputation,
            PricingAdjustmentApplication commissionApplication,
            PricingAdjustmentApplication discountApplication,
            CashbackApplication cashbackApplication
    ) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        BigDecimal dbOriginalAmount = originalAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dbNetAmount = netAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dbServiceChargeAmount = serviceChargeAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dbDiscountAmount = discountAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dbCommissionAmount = commissionAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dbCashbackAmount = cashbackAmount.multiply(currencyFactor).setScale(2, RoundingMode.HALF_UP);

        Transactions transaction = transactionsRepository.findByTransactionId(txnId);
        if (transaction != null) {
            transaction.setTransactionValue(dbNetAmount.add(dbServiceChargeAmount));
            transaction.setFeesDetails(buildPricingDetails(
                    serviceCode,
                    dbOriginalAmount,
                    originalAmount,
                    dbNetAmount,
                    netAmount,
                    dbServiceChargeAmount,
                    serviceChargeAmount,
                    dbDiscountAmount,
                    discountAmount,
                    dbCommissionAmount,
                    commissionAmount,
                    dbCashbackAmount,
                    cashbackAmount,
                    serviceChargePayer,
                    debitorWallet,
                    creditorWallet,
                    pricingComputation,
                    commissionApplication,
                    discountApplication,
                    cashbackApplication
            ));
            transactionsRepository.save(transaction);
        }

        updatePrimaryTransactionDetailAmounts(
                txnId,
                dbNetAmount,
                dbServiceChargeAmount,
                dbCommissionAmount,
                dbDiscountAmount
        );
    }

    private void updatePrimaryTransactionDetailAmounts(
            String txnId,
            BigDecimal dbNetAmount,
            BigDecimal dbServiceChargeAmount,
            BigDecimal dbCommissionAmount,
            BigDecimal dbDiscountAmount
    ) {
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(txnId).stream()
                .filter(transactionDetail -> transactionDetail.getId() != null)
                .filter(transactionDetail -> transactionDetail.getId().getTxnSequenceNumber() <= 2)
                .toList();
        for (TransactionDetails transactionDetail : transactionDetails) {
            transactionDetail.setTransactionValue(dbNetAmount);
            transactionDetail.setApprovedValue(dbNetAmount);
            setPrimaryPricingAttributes(
                    transactionDetail,
                    dbServiceChargeAmount,
                    dbCommissionAmount,
                    dbDiscountAmount
            );
        }
        transactionDetailsRepository.saveAll(transactionDetails);
    }

    private void setPrimaryPricingAttributes(
            TransactionDetails detail,
            BigDecimal dbServiceChargeAmount,
            BigDecimal dbCommissionAmount,
            BigDecimal dbDiscountAmount
    ) {
        List<String> names = new ArrayList<>();
        List<String> amounts = new ArrayList<>();
        addPricingAttribute(names, amounts, "SERVICE_CHARGE", dbServiceChargeAmount);
        addPricingAttribute(names, amounts, "COMMISSION", dbCommissionAmount);
        addPricingAttribute(names, amounts, "DISCOUNT", dbDiscountAmount);
        if (!names.isEmpty()) {
            detail.setAttr6Name(String.join(";", names));
            detail.setAttr6Value(String.join(";", amounts));
        }
    }

    private void addPricingAttribute(
            List<String> names,
            List<String> amounts,
            String name,
            BigDecimal amount
    ) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            names.add(name);
            amounts.add(amount.toPlainString());
        }
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
                transactionDetail.setTransactionType(Constants.TXN_DETAIL_TYPE_MONEY_PAID);
                transactionDetail.setPreviousBalance(senderBalBefore);
                transactionDetail.setPostBalance(senderBalAfter);
                transactionDetail.setPreviousFrozenBalance(senderFrozenBefore);
                transactionDetail.setPostFrozenBalance(senderFrozenAfter);
                transactionDetail.setPreviousFicBalance(senderFicBefore);
                transactionDetail.setPostFicBalance(senderFicAfter);
            }
            if (transactionDetail.getEntryType().equalsIgnoreCase(Constants.TXN_TYPE_CR)) {
                transactionDetail.setTransactionType(Constants.TXN_DETAIL_TYPE_MONEY_RECEIVED);
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

    private Map<Long, WalletBalance> lockBalancesInWalletIdOrder(Wallet... wallets) {
        Set<Long> walletIds = Arrays.stream(wallets)
                .map(Wallet::getWalletId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return walletIds.stream()
                .sorted()
                .collect(Collectors.toMap(
                        walletId -> walletId,
                        this::lockBalance,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ));
    }

    private Wallet getActiveSystemWallet(Wallet serviceChargeWallet) {
        return getActiveSystemWallet(serviceChargeWallet, SERVICE_CHARGE_WALLET_TYPE, "service charge");
    }

    private Wallet getActiveSystemWallet(Wallet referenceWallet, String walletType, String purpose) {
        Wallet systemWallet = walletRepository.findByAccountIdAndCurrencyAndWalletType(
                        SYSTEM_ACCOUNT_ID,
                        referenceWallet.getCurrency(),
                        walletType
                )
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.SYSTEM_WALLET_NOT_FOUND,
                        "System wallet not found for " + purpose
                ));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(systemWallet.getStatus())) {
            throw new ApplicationException(ErrorCodes.SYSTEM_WALLET_INACTIVE, "System wallet is not active");
        }

        if (Boolean.TRUE.equals(systemWallet.getIsLocked())) {
            throw new ApplicationException(ErrorCodes.SYSTEM_WALLET_INACTIVE, "System wallet is locked");
        }

        return systemWallet;
    }

    private Wallet getActiveAccountWallet(String accountId, String currency, String walletType, String purpose) {
        return findActiveAccountWallet(accountId, currency, walletType)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.INVALID_WALLET_NO,
                        "Active " + walletType + " wallet not found for " + purpose
                ));
    }

    private Optional<Wallet> findActiveAccountWallet(String accountId, String currency, String walletType) {
        return walletRepository.findByAccountIdAndCurrencyAndWalletType(accountId, currency, walletType)
                .filter(wallet -> Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus()))
                .filter(wallet -> !Boolean.TRUE.equals(wallet.getIsLocked()));
    }

    private String buildServiceChargeDetails(
            String serviceCode,
            BigDecimal dbAmount,
            BigDecimal requestAmount,
            BigDecimal dbServiceChargeAmount,
            BigDecimal serviceChargeAmount,
            String serviceChargePayer,
            Wallet serviceChargeWallet,
            Wallet systemWallet,
            PricingComputationResponse pricingComputation
    ) {
        JSONObject root = new JSONObject();
        JSONObject serviceCharge = new JSONObject();
        serviceCharge.put("description", "Service charge debited from %s and credited to the system wallet"
                .formatted(serviceChargePayer));
        serviceCharge.put("serviceCode", serviceCode);
        serviceCharge.put("ledgerTxnType", serviceCode);
        serviceCharge.put("detailTableEntry", false);
        serviceCharge.put("amount", dbServiceChargeAmount);
        serviceCharge.put("requestAmount", serviceChargeAmount);
        serviceCharge.put("transactionAmount", dbAmount);
        serviceCharge.put("requestTransactionAmount", requestAmount);
        serviceCharge.put("totalAmount", dbAmount.add(dbServiceChargeAmount));
        serviceCharge.put("requestTotalAmount", requestAmount.add(serviceChargeAmount));
        serviceCharge.put("payer", serviceChargePayer);
        serviceCharge.put("payerAccountId", serviceChargeWallet.getAccountId());
        serviceCharge.put("payerWalletId", serviceChargeWallet.getWalletId());
        serviceCharge.put("creditedAccountId", systemWallet.getAccountId());
        serviceCharge.put("creditedWalletId", systemWallet.getWalletId());
        serviceCharge.put("currency", trimCurrency(systemWallet.getCurrency()));
        serviceCharge.put("pricingRules", buildPricingRules(pricingComputation));
        root.put("serviceCharge", serviceCharge);
        return root.toString();
    }

    private String buildPricingDetails(
            String serviceCode,
            BigDecimal dbOriginalAmount,
            BigDecimal requestOriginalAmount,
            BigDecimal dbNetAmount,
            BigDecimal requestNetAmount,
            BigDecimal dbServiceChargeAmount,
            BigDecimal requestServiceChargeAmount,
            BigDecimal dbDiscountAmount,
            BigDecimal requestDiscountAmount,
            BigDecimal dbCommissionAmount,
            BigDecimal requestCommissionAmount,
            BigDecimal dbCashbackAmount,
            BigDecimal requestCashbackAmount,
            String serviceChargePayer,
            Wallet debitorWallet,
            Wallet creditorWallet,
            PricingComputationResponse pricingComputation,
            PricingAdjustmentApplication commissionApplication,
            PricingAdjustmentApplication discountApplication,
            CashbackApplication cashbackApplication
    ) {
        JSONObject root = new JSONObject();
        JSONObject summary = new JSONObject();
        summary.put("serviceCode", serviceCode);
        summary.put("originalTransactionAmount", dbOriginalAmount);
        summary.put("requestOriginalTransactionAmount", requestOriginalAmount);
        summary.put("discountAmount", dbDiscountAmount);
        summary.put("requestDiscountAmount", requestDiscountAmount);
        summary.put("netTransactionAmount", dbNetAmount);
        summary.put("requestNetTransactionAmount", requestNetAmount);
        summary.put("serviceChargeAmount", dbServiceChargeAmount);
        summary.put("requestServiceChargeAmount", requestServiceChargeAmount);
        summary.put("commissionAmount", dbCommissionAmount);
        summary.put("requestCommissionAmount", requestCommissionAmount);
        summary.put("cashbackAmount", dbCashbackAmount);
        summary.put("requestCashbackAmount", requestCashbackAmount);
        summary.put("totalTransactionValue", dbNetAmount.add(dbServiceChargeAmount));
        summary.put("requestTotalTransactionValue", requestNetAmount.add(requestServiceChargeAmount));
        summary.put("currency", trimCurrency(debitorWallet.getCurrency()));
        root.put("summary", summary);

        if (dbServiceChargeAmount.compareTo(BigDecimal.ZERO) > 0
                && isWalletServiceCharge(requestServiceChargeAmount, serviceChargePayer)) {
            String normalizedPayer = serviceChargePayer.trim().toUpperCase();
            Wallet serviceChargeWallet = SERVICE_CHARGE_PAYER_RECEIVER.equals(normalizedPayer)
                    ? creditorWallet
                    : debitorWallet;
            Wallet systemWallet = getActiveSystemWallet(serviceChargeWallet);
            JSONObject serviceCharge = new JSONObject();
            serviceCharge.put("description", "Service charge debited from %s and credited to the system wallet"
                    .formatted(normalizedPayer));
            serviceCharge.put("serviceCode", serviceCode);
            serviceCharge.put("ledgerTxnType", serviceCode);
            serviceCharge.put("detailTableEntry", false);
            serviceCharge.put("amount", dbServiceChargeAmount);
            serviceCharge.put("requestAmount", requestServiceChargeAmount);
            serviceCharge.put("transactionAmount", dbNetAmount);
            serviceCharge.put("requestTransactionAmount", requestNetAmount);
            serviceCharge.put("totalAmount", dbNetAmount.add(dbServiceChargeAmount));
            serviceCharge.put("requestTotalAmount", requestNetAmount.add(requestServiceChargeAmount));
            serviceCharge.put("payer", normalizedPayer);
            serviceCharge.put("payerAccountId", serviceChargeWallet.getAccountId());
            serviceCharge.put("payerWalletId", serviceChargeWallet.getWalletId());
            serviceCharge.put("creditedAccountId", systemWallet.getAccountId());
            serviceCharge.put("creditedWalletId", systemWallet.getWalletId());
            serviceCharge.put("currency", trimCurrency(systemWallet.getCurrency()));
            serviceCharge.put("pricingRules", buildPricingRules(
                    pricingComputation == null ? null : pricingComputation.getServiceChargeRules()
            ));
            root.put("serviceCharge", serviceCharge);
        }

        if (dbDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            JSONObject discount = new JSONObject();
            discount.put("description", "Discount reduced the transaction amount before balance movement");
            discount.put("serviceCode", serviceCode);
            discount.put("amount", dbDiscountAmount);
            discount.put("requestAmount", requestDiscountAmount);
            discount.put("originalTransactionAmount", dbOriginalAmount);
            discount.put("requestOriginalTransactionAmount", requestOriginalAmount);
            discount.put("netTransactionAmount", dbNetAmount);
            discount.put("requestNetTransactionAmount", requestNetAmount);
            discount.put("affectedParty", pricingComputation == null
                    ? JSONObject.NULL
                    : pricingComputation.getDiscountAffectedParty());
            if (discountApplication != null && discountApplication.applied) {
                discount.put("fundingDescription", "Discount debited from system commission wallet and credited to beneficiary main wallet");
                discount.put("transactionDetailSequences", new JSONArray(List.of(
                        discountApplication.debitSequenceNumber,
                        discountApplication.creditSequenceNumber
                )));
                discount.put("debitedAccountId", discountApplication.sourceWallet.getAccountId());
                discount.put("debitedWalletId", discountApplication.sourceWallet.getWalletId());
                discount.put("creditedAccountId", discountApplication.beneficiaryWallet.getAccountId());
                discount.put("creditedWalletId", discountApplication.beneficiaryWallet.getWalletId());
            }
            discount.put("currency", trimCurrency(debitorWallet.getCurrency()));
            discount.put("pricingRules", buildPricingRules(
                    pricingComputation == null ? null : pricingComputation.getDiscountRules()
            ));
            root.put("discount", discount);
        }

        if (commissionApplication != null && commissionApplication.applied) {
            JSONObject commission = new JSONObject();
            commission.put("description", "Commission debited from system commission wallet and credited to beneficiary commission or main wallet");
            commission.put("serviceCode", serviceCode);
            commission.put("ledgerTxnType", serviceCode);
            commission.put("transactionDetailServiceCode", serviceCode);
            commission.put("transactionDetailSequences", new JSONArray(List.of(
                    commissionApplication.debitSequenceNumber,
                    commissionApplication.creditSequenceNumber
            )));
            commission.put("amount", commissionApplication.dbAmount);
            commission.put("requestAmount", commissionApplication.requestAmount);
            commission.put("beneficiary", commissionApplication.beneficiaryParty);
            commission.put("debitedAccountId", commissionApplication.sourceWallet.getAccountId());
            commission.put("debitedWalletId", commissionApplication.sourceWallet.getWalletId());
            commission.put("creditedAccountId", commissionApplication.beneficiaryWallet.getAccountId());
            commission.put("creditedWalletId", commissionApplication.beneficiaryWallet.getWalletId());
            commission.put("currency", trimCurrency(commissionApplication.beneficiaryWallet.getCurrency()));
            commission.put("pricingRules", buildPricingRules(
                    pricingComputation == null ? null : pricingComputation.getCommissionRules()
            ));
            root.put("commission", commission);
        }

        if (cashbackApplication != null && cashbackApplication.applied) {
            JSONObject cashback = new JSONObject();
            cashback.put("description", "Cashback scheduled for separate payout from system commission wallet");
            cashback.put("serviceCode", serviceCode);
            cashback.put("amount", cashbackApplication.dbAmount);
            cashback.put("requestAmount", cashbackApplication.requestAmount);
            cashback.put("payBy", cashbackApplication.payBy);
            cashback.put("beneficiary", cashbackApplication.beneficiaryParty);
            cashback.put("cashbackPayoutId", cashbackApplication.cashbackPayoutId);
            cashback.put("paymentSchedule", cashbackApplication.paymentSchedule);
            cashback.put("payAt", cashbackApplication.payAt == null
                    ? JSONObject.NULL
                    : cashbackApplication.payAt.toString());
            cashback.put("status", CashbackPayoutService.STATUS_PENDING);
            cashback.put("debitedAccountId", SYSTEM_ACCOUNT_ID);
            cashback.put("debitedWalletType", COMMISSION_WALLET_TYPE);
            cashback.put("creditedAccountId", cashbackApplication.beneficiaryWallet.getAccountId());
            cashback.put("creditedWalletType", "BONUS_OR_MAIN");
            cashback.put("currency", trimCurrency(cashbackApplication.beneficiaryWallet.getCurrency()));
            cashback.put("pricingRules", buildPricingRules(
                    pricingComputation == null ? null : pricingComputation.getCashbackRules()
            ));
            root.put("cashback", cashback);
        }

        return root.toString();
    }

    private JSONArray buildPricingRules(PricingComputationResponse pricingComputation) {
        if (pricingComputation == null) {
            return new JSONArray();
        }
        return buildPricingRules(pricingComputation.getServiceChargeRules());
    }

    private JSONArray buildPricingRules(List<PricingComputationResponse.PricingRuleDetails> ruleDetailsList) {
        JSONArray pricingRules = new JSONArray();
        if (ruleDetailsList == null) {
            return pricingRules;
        }

        for (PricingComputationResponse.PricingRuleDetails ruleDetails : ruleDetailsList) {
            JSONObject rule = new JSONObject();
            rule.put("id", ruleDetails.getId());
            rule.put("pricingName", ruleDetails.getPricingName());
            rule.put("serviceCode", ruleDetails.getServiceCode());
            rule.put("ruleType", ruleDetails.getRuleType());
            rule.put("pricingType", ruleDetails.getPricingType());
            rule.put("payer", ruleDetails.getPayer());
            rule.put("payBy", ruleDetails.getPayBy());
            rule.put("senderTagKey", ruleDetails.getSenderTagKey());
            rule.put("receiverTagKey", ruleDetails.getReceiverTagKey());
            rule.put("currency", trimCurrency(ruleDetails.getCurrency()));
            rule.put("calculatedAmount", ruleDetails.getCalculatedAmount());
            rule.put("pricingConfig", toJsonValue(ruleDetails.getPricingConfig()));
            pricingRules.put(rule);
        }
        return pricingRules;
    }

    private Object toJsonValue(com.fasterxml.jackson.databind.JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return JSONObject.NULL;
        }
        if (jsonNode.isObject()) {
            return new JSONObject(jsonNode.toString());
        }
        if (jsonNode.isArray()) {
            return new JSONArray(jsonNode.toString());
        }
        if (jsonNode.isNumber()) {
            return jsonNode.numberValue();
        }
        if (jsonNode.isBoolean()) {
            return jsonNode.booleanValue();
        }
        return jsonNode.asText();
    }

    private static final class PricingAdjustmentApplication {
        private final boolean applied;
        private final String adjustmentType;
        private final Wallet sourceWallet;
        private final Wallet beneficiaryWallet;
        private final BigDecimal dbAmount;
        private final BigDecimal requestAmount;
        private final String beneficiaryParty;
        private final Long debitSequenceNumber;
        private final Long creditSequenceNumber;

        private PricingAdjustmentApplication(
                boolean applied,
                String adjustmentType,
                Wallet sourceWallet,
                Wallet beneficiaryWallet,
                BigDecimal dbAmount,
                BigDecimal requestAmount,
                String beneficiaryParty,
                Long debitSequenceNumber,
                Long creditSequenceNumber
        ) {
            this.applied = applied;
            this.adjustmentType = adjustmentType;
            this.sourceWallet = sourceWallet;
            this.beneficiaryWallet = beneficiaryWallet;
            this.dbAmount = dbAmount;
            this.requestAmount = requestAmount;
            this.beneficiaryParty = beneficiaryParty;
            this.debitSequenceNumber = debitSequenceNumber;
            this.creditSequenceNumber = creditSequenceNumber;
        }

        private static PricingAdjustmentApplication none() {
            return new PricingAdjustmentApplication(
                    false,
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null
            );
        }
    }

    private static final class CashbackApplication {
        private final boolean applied;
        private final Wallet beneficiaryWallet;
        private final BigDecimal dbAmount;
        private final BigDecimal requestAmount;
        private final String payBy;
        private final String beneficiaryParty;
        private final String paymentSchedule;
        private final LocalDateTime payAt;
        private final Long cashbackPayoutId;

        private CashbackApplication(
                boolean applied,
                Wallet beneficiaryWallet,
                BigDecimal dbAmount,
                BigDecimal requestAmount,
                String payBy,
                String beneficiaryParty,
                String paymentSchedule,
                LocalDateTime payAt,
                Long cashbackPayoutId
        ) {
            this.applied = applied;
            this.beneficiaryWallet = beneficiaryWallet;
            this.dbAmount = dbAmount;
            this.requestAmount = requestAmount;
            this.payBy = payBy;
            this.beneficiaryParty = beneficiaryParty;
            this.paymentSchedule = paymentSchedule;
            this.payAt = payAt;
            this.cashbackPayoutId = cashbackPayoutId;
        }

        private static CashbackApplication none() {
            return new CashbackApplication(
                    false,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private String limitSecondIdentifier(String value) {
        String normalized = defaultIfBlank(value, "UNKNOWN");
        return normalized.length() <= 20 ? normalized : normalized.substring(0, 20);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimCurrency(String currency) {
        return currency == null ? null : currency.trim();
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
