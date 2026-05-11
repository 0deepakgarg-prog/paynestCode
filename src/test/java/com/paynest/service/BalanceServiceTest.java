package com.paynest.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.payments.service.BalanceService;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import com.paynest.exception.ApplicationException;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private com.paynest.payments.service.TransactionsService transactionsService;

    @Mock
    private WalletCacheService walletCacheService;

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
                ledgerRepo,
                transactionsService,
                walletCacheService
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
                    InitiatedBy.DEBITOR,
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
                        InitiatedBy.DEBITOR,
                        "txn-1"
                )
        );

        assertEquals("INSUFFICIENT_BALANCE", exception.getErrorCode());
        assertEquals("txn-1", exception.getTransactionId());
    }

    @Test
    void transferWalletAmountWithServiceCharge_shouldDebitSenderChargeAndCreditSystemWallet() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet creditorWallet = wallet(20L, "acc-2");
        Wallet systemWallet = wallet(30L, "SYS0001");

        WalletBalance debitorBalance = balance(10L, "5000.00");
        WalletBalance creditorBalance = balance(20L, "1000.00");
        WalletBalance systemBalance = balance(30L, "0.00");

        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-fee-1");
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);

        TransactionDetails debitDetail = transactionDetail("txn-fee-1", 1L, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = transactionDetail("txn-fee-1", 2L, Constants.TXN_TYPE_CR);
        debitDetail.setAccountId("acc-1");
        debitDetail.setUserType("SUBSCRIBER");
        debitDetail.setIdentifierId("9003832992");
        debitDetail.setSecondIdentifierId("9414473582");
        debitDetail.setWalletNumber("10");
        creditDetail.setAccountId("acc-2");
        creditDetail.setUserType("SUBSCRIBER");
        creditDetail.setIdentifierId("9414473582");
        creditDetail.setSecondIdentifierId("9003832992");
        creditDetail.setWalletNumber("20");

        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("SYS0001", "USD", "MAIN"))
                .thenReturn(java.util.Optional.of(systemWallet));
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(balanceRepository.lockBalance(10L)).thenReturn(debitorBalance);
        when(balanceRepository.lockBalance(20L)).thenReturn(creditorBalance);
        when(balanceRepository.lockBalance(30L)).thenReturn(systemBalance);
        when(transactionsRepository.findByTransactionId("txn-fee-1")).thenReturn(transaction);
        when(transactionDetailsRepository.findByIdTransactionId("txn-fee-1")).thenReturn(List.of(debitDetail, creditDetail));

        TraceContext.setTraceId("trace-fee-1");
        try {
            balanceService.transferWalletAmountWithServiceCharge(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.00"),
                    new BigDecimal("1.50"),
                    "SENDER",
                    "U2U",
                    InitiatedBy.DEBITOR,
                    "txn-fee-1"
            );
        } finally {
            TraceContext.clear();
        }

        assertEquals(new BigDecimal("3850.00"), debitorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("2000.00"), creditorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("150.00"), systemBalance.getAvailableBalance());
        assertEquals(Constants.TRANSACTION_SUCCESS, transaction.getTransferStatus());
        assertEquals(new BigDecimal("1150.00"), transaction.getTransactionValue());
        assertEquals(new BigDecimal("5000.00"), debitDetail.getPreviousBalance());
        assertEquals(new BigDecimal("4000.00"), debitDetail.getPostBalance());
        assertEquals(new BigDecimal("1000.00"), creditDetail.getPreviousBalance());
        assertEquals(new BigDecimal("2000.00"), creditDetail.getPostBalance());
        org.junit.jupiter.api.Assertions.assertTrue(transaction.getFeesDetails().contains("\"payer\":\"SENDER\""));
        org.junit.jupiter.api.Assertions.assertTrue(transaction.getFeesDetails().contains("\"transactionDetailServiceCode\":\"U2U\""));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionDetailsRepository, org.mockito.Mockito.times(2)).saveAll(detailsCaptor.capture());
        List<TransactionDetails> feeDetails = detailsCaptor.getAllValues().get(1);
        assertEquals(2, feeDetails.size());
        assertEquals(Constants.TXN_TYPE_DR, feeDetails.get(0).getEntryType());
        assertEquals(Constants.TXN_TYPE_CR, feeDetails.get(1).getEntryType());
        assertEquals("U2U", feeDetails.get(0).getServiceCode());
        assertEquals("SUBSCRIBER", feeDetails.get(0).getUserType());
        assertEquals("9003832992", feeDetails.get(0).getIdentifierId());
        assertEquals("30", feeDetails.get(0).getSecondIdentifierId());
        assertEquals(new BigDecimal("150.00"), feeDetails.get(0).getTransactionValue());
        assertEquals(BigDecimal.ZERO, feeDetails.get(0).getPreviousFrozenBalance());
        assertEquals(BigDecimal.ZERO, feeDetails.get(0).getPostFrozenBalance());
        assertEquals(BigDecimal.ZERO, feeDetails.get(0).getPreviousFicBalance());
        assertEquals(BigDecimal.ZERO, feeDetails.get(0).getPostFicBalance());
        assertEquals("SYSTEM", feeDetails.get(1).getUserType());
        assertEquals("30", feeDetails.get(1).getIdentifierId());
        assertEquals("9003832992", feeDetails.get(1).getSecondIdentifierId());
    }

    @Test
    void transferWalletAmountWithPricing_shouldApplyDiscountServiceChargeAndCashback() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet creditorWallet = wallet(20L, "acc-2");
        Wallet systemWallet = wallet(30L, "SYS0001");

        WalletBalance debitorBalance = balance(10L, "5000.00");
        WalletBalance creditorBalance = balance(20L, "1000.00");
        WalletBalance systemBalance = balance(30L, "1000.00");

        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-pricing-1");
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);

        TransactionDetails debitDetail = transactionDetail("txn-pricing-1", 1L, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = transactionDetail("txn-pricing-1", 2L, Constants.TXN_TYPE_CR);
        debitDetail.setAccountId("acc-1");
        debitDetail.setUserType("SUBSCRIBER");
        debitDetail.setIdentifierId("9003832992");
        debitDetail.setSecondIdentifierId("9414473582");
        debitDetail.setWalletNumber("10");
        creditDetail.setAccountId("acc-2");
        creditDetail.setUserType("SUBSCRIBER");
        creditDetail.setIdentifierId("9414473582");
        creditDetail.setSecondIdentifierId("9003832992");
        creditDetail.setWalletNumber("20");

        PricingComputationResponse pricingComputation = new PricingComputationResponse();
        pricingComputation.addServiceCharge(new BigDecimal("1.50"));
        pricingComputation.markServiceChargeAffectedParty("SENDER");
        pricingComputation.addDiscount(new BigDecimal("2.00"));
        pricingComputation.markDiscountAffectedParty("SENDER");
        pricingComputation.addCashback(new BigDecimal("0.50"));
        pricingComputation.markCashbackAffectedParty("RECEIVER");
        pricingComputation.markCashbackPayBy("SYSTEM");

        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("SYS0001", "USD", "MAIN"))
                .thenReturn(java.util.Optional.of(systemWallet));
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(balanceRepository.lockBalance(10L)).thenReturn(debitorBalance);
        when(balanceRepository.lockBalance(20L)).thenReturn(creditorBalance, creditorBalance);
        when(balanceRepository.lockBalance(30L)).thenReturn(systemBalance, systemBalance);
        when(transactionsRepository.findByTransactionId("txn-pricing-1")).thenReturn(transaction);
        when(transactionDetailsRepository.findByIdTransactionId("txn-pricing-1"))
                .thenReturn(List.of(debitDetail, creditDetail));

        balanceService.transferWalletAmountWithPricing(
                debitorWallet,
                creditorWallet,
                new BigDecimal("10.00"),
                "U2U",
                InitiatedBy.DEBITOR,
                "txn-pricing-1",
                pricingComputation
        );

        assertEquals(new BigDecimal("4050.00"), debitorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("1850.00"), creditorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("1100.00"), systemBalance.getAvailableBalance());
        assertEquals(new BigDecimal("950.00"), transaction.getTransactionValue());
        assertEquals(new BigDecimal("800.00"), debitDetail.getTransactionValue());
        assertEquals(new BigDecimal("800.00"), creditDetail.getTransactionValue());
        org.junit.jupiter.api.Assertions.assertTrue(transaction.getFeesDetails().contains("\"discount\""));
        org.junit.jupiter.api.Assertions.assertTrue(transaction.getFeesDetails().contains("\"cashback\""));
        org.junit.jupiter.api.Assertions.assertTrue(transaction.getFeesDetails().contains("\"netTransactionAmount\":800"));

        ArgumentCaptor<WalletLedger> ledgerCaptor = ArgumentCaptor.forClass(WalletLedger.class);
        verify(ledgerRepo, org.mockito.Mockito.times(6)).save(ledgerCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(ledgerCaptor.getAllValues().stream()
                .allMatch(ledger -> "U2U".equals(ledger.getTxnType())));
        assertEquals(Constants.TXN_TYPE_DR, ledgerCaptor.getAllValues().get(4).getEntryType());
        assertEquals(Constants.TXN_TYPE_CR, ledgerCaptor.getAllValues().get(5).getEntryType());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionDetailsRepository, org.mockito.Mockito.times(4)).saveAll(detailsCaptor.capture());
        List<TransactionDetails> cashbackDetails = detailsCaptor.getAllValues().get(2);
        assertEquals(5L, cashbackDetails.get(0).getId().getTxnSequenceNumber());
        assertEquals(6L, cashbackDetails.get(1).getId().getTxnSequenceNumber());
        assertEquals("U2U", cashbackDetails.get(0).getServiceCode());
        assertEquals("SYSTEM", cashbackDetails.get(0).getUserType());
        assertEquals("SUBSCRIBER", cashbackDetails.get(1).getUserType());
        assertEquals("9414473582", cashbackDetails.get(1).getIdentifierId());
    }

    @Test
    void parkWalletAmountInFicWithServiceCharge_shouldParkFundsAndDebitSenderCharge() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet creditorWallet = wallet(20L, "biller-1");
        Wallet systemWallet = wallet(30L, "SYS0001");

        WalletBalance debitorBalance = balance(10L, "5000.00");
        WalletBalance creditorBalance = balance(20L, "1000.00");
        WalletBalance systemBalance = balance(30L, "0.00");

        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-bill-fee-1");
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);

        TransactionDetails debitDetail = transactionDetail("txn-bill-fee-1", 1L, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = transactionDetail("txn-bill-fee-1", 2L, Constants.TXN_TYPE_CR);
        debitDetail.setAccountId("acc-1");
        debitDetail.setUserType("SUBSCRIBER");
        debitDetail.setIdentifierId("9003832992");
        debitDetail.setSecondIdentifierId("9414473582");
        debitDetail.setWalletNumber("10");
        creditDetail.setAccountId("biller-1");
        creditDetail.setUserType("BILLER");
        creditDetail.setIdentifierId("9414473582");
        creditDetail.setSecondIdentifierId("9003832992");
        creditDetail.setWalletNumber("20");

        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("SYS0001", "USD", "MAIN"))
                .thenReturn(java.util.Optional.of(systemWallet));
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(balanceRepository.lockBalance(10L)).thenReturn(debitorBalance);
        when(balanceRepository.lockBalance(20L)).thenReturn(creditorBalance);
        when(balanceRepository.lockBalance(30L)).thenReturn(systemBalance);
        when(transactionsRepository.findByTransactionId("txn-bill-fee-1")).thenReturn(transaction);
        when(transactionDetailsRepository.findByIdTransactionId("txn-bill-fee-1")).thenReturn(List.of(debitDetail, creditDetail));

        TraceContext.setTraceId("trace-bill-fee-1");
        try {
            balanceService.parkWalletAmountInFicWithServiceCharge(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.00"),
                    new BigDecimal("1.50"),
                    "SENDER",
                    "BILLPAY",
                    InitiatedBy.DEBITOR,
                    "txn-bill-fee-1"
            );
        } finally {
            TraceContext.clear();
        }

        assertEquals(new BigDecimal("3850.00"), debitorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("2000.00"), creditorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("1000.00"), creditorBalance.getFicBalance());
        assertEquals(new BigDecimal("150.00"), systemBalance.getAvailableBalance());
        assertEquals(Constants.TRANSACTION_AMBIGUOUS, transaction.getTransferStatus());
        assertEquals(new BigDecimal("1150.00"), transaction.getTransactionValue());
        assertEquals(new BigDecimal("5000.00"), debitDetail.getPreviousBalance());
        assertEquals(new BigDecimal("4000.00"), debitDetail.getPostBalance());
        assertEquals(new BigDecimal("1000.00"), creditDetail.getPreviousBalance());
        assertEquals(new BigDecimal("2000.00"), creditDetail.getPostBalance());
        assertEquals(new BigDecimal("1000.00"), creditDetail.getPostFicBalance());
        org.junit.jupiter.api.Assertions.assertTrue(transaction.getFeesDetails().contains("\"payer\":\"SENDER\""));
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
                    InitiatedBy.DEBITOR,
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
        wallet.setWalletType("MAIN");
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
