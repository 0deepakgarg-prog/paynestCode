package com.paynest.users.service;

import com.paynest.exception.ApplicationException;
import com.paynest.limits.TransactionLimitErrorCode;
import com.paynest.limits.service.TransactionLimitValidator;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletBalanceRepository balanceRepo;

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private WalletLedgerRepository ledgerRepo;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletCacheService walletCacheService;

    @Mock
    private TransactionLimitValidator transactionLimitValidator;

    @InjectMocks
    private WalletService walletService;

    @Test
    void debitWallet_shouldValidateLimitBeforePostingDebit() {
        Wallet wallet = wallet();
        WalletBalance balance = balance("2000.00");
        BigDecimal amount = new BigDecimal("500.00");
        BigDecimal expectedAfter = new BigDecimal("1500.00");

        when(balanceRepo.lockBalance(101L)).thenReturn(balance);

        walletService.debitWallet(wallet, amount, "txn-1", "ACCOUNT_DELETION");

        InOrder inOrder = inOrder(transactionLimitValidator, ledgerRepo, balanceRepo);
        inOrder.verify(transactionLimitValidator).validateAndReserve(
                eq(wallet),
                isNull(),
                eq(amount),
                eq(BigDecimal.ZERO),
                eq(expectedAfter),
                isNull(),
                eq("ACCOUNT_DELETION"),
                eq("txn-1")
        );
        inOrder.verify(ledgerRepo).save(any(WalletLedger.class));
        inOrder.verify(balanceRepo).save(balance);
        assertEquals(expectedAfter, balance.getAvailableBalance());
        verify(walletCacheService).refreshAccountWallets("acc-1");
    }

    @Test
    void creditWallet_shouldValidateLimitBeforePostingCredit() {
        Wallet wallet = wallet();
        WalletBalance balance = balance("2000.00");
        BigDecimal amount = new BigDecimal("500.00");
        BigDecimal expectedAfter = new BigDecimal("2500.00");

        when(balanceRepo.lockBalance(101L)).thenReturn(balance);

        walletService.creditWallet(wallet, amount, "txn-1", "CASHBACK");

        InOrder inOrder = inOrder(transactionLimitValidator, ledgerRepo, balanceRepo);
        inOrder.verify(transactionLimitValidator).validateAndReserve(
                isNull(),
                eq(wallet),
                eq(BigDecimal.ZERO),
                eq(amount),
                isNull(),
                eq(expectedAfter),
                eq("CASHBACK"),
                eq("txn-1")
        );
        inOrder.verify(ledgerRepo).save(any(WalletLedger.class));
        inOrder.verify(balanceRepo).save(balance);
        assertEquals(expectedAfter, balance.getAvailableBalance());
        verify(walletCacheService).refreshAccountWallets("acc-1");
    }

    @Test
    void debitWallet_shouldNotPostDebitWhenLimitValidationFails() {
        Wallet wallet = wallet();
        WalletBalance balance = balance("2000.00");
        BigDecimal amount = new BigDecimal("500.00");

        when(balanceRepo.lockBalance(101L)).thenReturn(balance);
        doThrow(new ApplicationException(TransactionLimitErrorCode.LIMIT_PROFILE_NOT_FOUND, "Limit profile not found", java.util.Map.of()))
                .when(transactionLimitValidator)
                .validateAndReserve(
                        eq(wallet),
                        isNull(),
                        eq(amount),
                        eq(BigDecimal.ZERO),
                        eq(new BigDecimal("1500.00")),
                        isNull(),
                        eq("ACCOUNT_DELETION"),
                        eq("txn-1")
                );

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> walletService.debitWallet(wallet, amount, "txn-1", "ACCOUNT_DELETION")
        );

        assertEquals(TransactionLimitErrorCode.LIMIT_PROFILE_NOT_FOUND.code(), exception.getErrorCode());
        verify(ledgerRepo, never()).save(any(WalletLedger.class));
        verify(balanceRepo, never()).save(any(WalletBalance.class));
        assertEquals(new BigDecimal("2000.00"), balance.getAvailableBalance());
    }

    private Wallet wallet() {
        Wallet wallet = new Wallet();
        wallet.setWalletId(101L);
        wallet.setAccountId("acc-1");
        wallet.setWalletType("MAIN");
        wallet.setCurrency("USD");
        return wallet;
    }

    private WalletBalance balance(String availableBalance) {
        WalletBalance balance = new WalletBalance();
        balance.setWalletId(101L);
        balance.setAvailableBalance(new BigDecimal(availableBalance));
        balance.setFrozenBalance(BigDecimal.ZERO);
        balance.setFicBalance(BigDecimal.ZERO);
        return balance;
    }
}
