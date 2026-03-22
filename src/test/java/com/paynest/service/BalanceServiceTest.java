package com.paynest.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.entity.TransactionDetails;
import com.paynest.entity.TransactionDetailsId;
import com.paynest.entity.Transactions;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.TransactionDetailsRepository;
import com.paynest.repository.TransactionsRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletLedgerRepository;
import com.paynest.repository.WalletRepository;
import com.paynest.tenant.TraceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletBalanceRepository balanceRepository;

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private PropertyReader propertyReader;

    @Mock
    private WalletLedgerRepository ledgerRepo;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(
                walletRepository,
                balanceRepository,
                accountRepo,
                transactionsRepository,
                transactionDetailsRepository,
                propertyReader,
                balanceRepository,
                ledgerRepo
        );
    }

    @Test
    void transferWalletAmount_shouldLockBalancesInWalletIdOrder() {
        Wallet debitorWallet = wallet(20L, "acc-1");
        Wallet creditorWallet = wallet(10L, "acc-2");

        WalletBalance creditorBalance = balance(10L, "5000.00");
        WalletBalance debitorBalance = balance(20L, "5000.00");

        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-1");
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);

        TransactionDetails debitDetail = transactionDetail("txn-1", 1L, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = transactionDetail("txn-1", 2L, Constants.TXN_TYPE_CR);

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(balanceRepository.lockBalance(10L)).thenReturn(creditorBalance);
        when(balanceRepository.lockBalance(20L)).thenReturn(debitorBalance);
        when(transactionsRepository.findByTransactionId("txn-1")).thenReturn(transaction);
        when(transactionDetailsRepository.findByIdTransactionId("txn-1")).thenReturn(List.of(debitDetail, creditDetail));

        TraceContext.setTraceId("trace-1");
        try {
            balanceService.transferWalletAmount(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.00"),
                    "U2U",
                    com.paynest.enums.InitiatedBy.DEBITOR,
                    "txn-1"
            );
        } finally {
            TraceContext.clear();
        }

        var inOrder = inOrder(balanceRepository);
        inOrder.verify(balanceRepository).lockBalance(10L);
        inOrder.verify(balanceRepository).lockBalance(20L);
        verify(balanceRepository).save(debitorBalance);
        verify(balanceRepository).save(creditorBalance);
    }

    @Test
    void transferWalletAmount_shouldIncludeTransactionIdWhenBalanceIsInsufficient() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet creditorWallet = wallet(20L, "acc-2");

        WalletBalance debitorBalance = balance(10L, "5.00");
        WalletBalance creditorBalance = balance(20L, "5000.00");

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(balanceRepository.lockBalance(10L)).thenReturn(debitorBalance);
        when(balanceRepository.lockBalance(20L)).thenReturn(creditorBalance);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> balanceService.transferWalletAmount(
                        debitorWallet,
                        creditorWallet,
                        new BigDecimal("10.00"),
                        "U2U",
                        com.paynest.enums.InitiatedBy.DEBITOR,
                        "txn-1"
                )
        );

        assertEquals("INSUFFICIENT_BALANCE", exception.getErrorCode());
        assertEquals("txn-1", exception.getTransactionId());
    }

    @Test
    void parkWalletAmountInFic_shouldMoveCreditorFundsToAvailableAndFicAndMarkTransactionAmbiguous() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet creditorWallet = wallet(20L, "acc-2");

        WalletBalance debitorBalance = balance(10L, "5000.00");
        WalletBalance creditorBalance = balance(20L, "1000.00");

        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-2");
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);

        TransactionDetails debitDetail = transactionDetail("txn-2", 1L, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = transactionDetail("txn-2", 2L, Constants.TXN_TYPE_CR);

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(balanceRepository.lockBalance(10L)).thenReturn(debitorBalance);
        when(balanceRepository.lockBalance(20L)).thenReturn(creditorBalance);
        when(transactionsRepository.findByTransactionId("txn-2")).thenReturn(transaction);
        when(transactionDetailsRepository.findByIdTransactionId("txn-2")).thenReturn(List.of(debitDetail, creditDetail));

        TraceContext.setTraceId("trace-2");
        try {
            balanceService.parkWalletAmountInFic(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.00"),
                    "BILLPAY",
                    com.paynest.enums.InitiatedBy.DEBITOR,
                    "txn-2"
            );
        } finally {
            TraceContext.clear();
        }

        assertEquals(new BigDecimal("4000.00"), debitorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("2000.00"), creditorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("1000.00"), creditorBalance.getFicBalance());
        assertEquals(Constants.TRANSACTION_AMBIGUOUS, transaction.getTransferStatus());
        assertEquals(Constants.TRANSACTION_AMBIGUOUS, creditDetail.getTransferStatus());
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency("USD");
        wallet.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return wallet;
    }

    private WalletBalance balance(Long walletId, String amount) {
        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setWalletId(walletId);
        walletBalance.setAvailableBalance(new BigDecimal(amount));
        walletBalance.setFrozenBalance(BigDecimal.ZERO);
        walletBalance.setFicBalance(BigDecimal.ZERO);
        return walletBalance;
    }

    private TransactionDetails transactionDetail(String transactionId, Long sequence, String entryType) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, sequence));
        detail.setEntryType(entryType);
        return detail;
    }
}
