package com.paynest.payment.service;

import com.paynest.common.Constants;
import com.paynest.entity.TransactionDetails;
import com.paynest.entity.TransactionDetailsId;
import com.paynest.entity.Transactions;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.enums.TransactionStatus;
import com.paynest.exception.ApplicationException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionSettlementServiceTest {

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private TransactionsService transactionsService;

    @Test
    void settleTransaction_shouldClearFicAndMarkTransactionSuccessful() {
        TransactionSettlementService service = service();
        Transactions transaction = ambiguousTransaction();
        TransactionDetails debitDetail = debitDetail("txn-1", "10");
        TransactionDetails creditDetail = creditDetail("txn-1", "20");
        Wallet creditorWallet = wallet(20L, "biller-1");
        WalletBalance creditorBalance = walletBalance(20L, "2000.00", "1000.00");

        when(transactionsRepository.findFirstByTraceId("trace-1")).thenReturn(Optional.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionId("txn-1")).thenReturn(List.of(debitDetail, creditDetail));
        when(walletRepository.findById(10L)).thenReturn(Optional.of(wallet(10L, "sub-1")));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(creditorWallet));
        when(walletBalanceRepository.lockBalance(20L)).thenReturn(creditorBalance);

        SettleTransactionRequest request = new SettleTransactionRequest();
        request.setTraceId("trace-1");
        request.setSettlementStatus(true);
        request.setComments("settled");
        request.setAdditionalInfo(java.util.Map.of("providerRef", "ABC-1"));

        TraceContext.setTraceId("settle-trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("ops-1");

            SettleTransactionResponse response = service.settleTransaction(request);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals(Constants.TRANSACTION_SUCCESS, response.getTransferStatus());
            assertEquals("txn-1", response.getTransactionId());
            assertEquals(0, BigDecimal.ZERO.compareTo(creditorBalance.getFicBalance()));
            assertEquals(Constants.TRANSACTION_SUCCESS, transaction.getTransferStatus());

            verify(walletBalanceRepository).save(creditorBalance);
            verify(transactionsService).updateComments("txn-1", "settled");
            ArgumentCaptor<JSONObject> additionalInfoCaptor = ArgumentCaptor.forClass(JSONObject.class);
            verify(transactionsService).updateAdditionalInfo(any(), additionalInfoCaptor.capture());
            assertTrue(additionalInfoCaptor.getValue().toString().contains("\"providerRef\":\"ABC-1\""));
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void settleTransaction_shouldRollbackAndMarkTransactionFailed() {
        TransactionSettlementService service = service();
        Transactions transaction = ambiguousTransaction();
        TransactionDetails debitDetail = debitDetail("txn-1", "10");
        TransactionDetails creditDetail = creditDetail("txn-1", "20");
        Wallet debitorWallet = wallet(10L, "sub-1");
        Wallet creditorWallet = wallet(20L, "biller-1");
        WalletBalance debitorBalance = walletBalance(10L, "4000.00", "0.00");
        WalletBalance creditorBalance = walletBalance(20L, "2000.00", "1000.00");

        when(transactionsRepository.findFirstByTraceId("trace-1")).thenReturn(Optional.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionId("txn-1")).thenReturn(List.of(debitDetail, creditDetail));
        when(walletRepository.findById(10L)).thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(creditorWallet));
        when(walletBalanceRepository.lockBalance(10L)).thenReturn(debitorBalance);
        when(walletBalanceRepository.lockBalance(20L)).thenReturn(creditorBalance);

        SettleTransactionRequest request = new SettleTransactionRequest();
        request.setTraceId("trace-1");
        request.setSettlementStatus(false);

        TraceContext.setTraceId("settle-trace-2");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("ops-1");

            SettleTransactionResponse response = service.settleTransaction(request);

            assertEquals(Constants.TRANSACTION_FAILED, response.getTransferStatus());
            assertEquals(new BigDecimal("5000.00"), debitorBalance.getAvailableBalance());
            assertEquals(new BigDecimal("1000.00"), creditorBalance.getAvailableBalance());
            assertEquals(0, BigDecimal.ZERO.compareTo(creditorBalance.getFicBalance()));
            assertEquals(Constants.TRANSACTION_FAILED, transaction.getTransferStatus());

            verify(walletLedgerRepository, times(2)).save(any());
            verify(transactionsService, never()).updateComments(any(), any());
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void settleTransaction_shouldRejectWhenTransactionIsNotAmbiguous() {
        TransactionSettlementService service = service();
        Transactions transaction = ambiguousTransaction();
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);

        when(transactionsRepository.findFirstByTraceId("trace-1")).thenReturn(Optional.of(transaction));

        SettleTransactionRequest request = new SettleTransactionRequest();
        request.setTraceId("trace-1");
        request.setSettlementStatus(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.settleTransaction(request)
        );

        assertEquals("TRANSACTION_NOT_PENDING_SETTLEMENT", exception.getErrorCode());
    }

    @Test
    void settleTransaction_shouldRejectWhenFicBalanceIsInsufficient() {
        TransactionSettlementService service = service();
        Transactions transaction = ambiguousTransaction();
        TransactionDetails debitDetail = debitDetail("txn-1", "10");
        TransactionDetails creditDetail = creditDetail("txn-1", "20");
        Wallet creditorWallet = wallet(20L, "biller-1");
        WalletBalance creditorBalance = walletBalance(20L, "2000.00", "500.00");

        when(transactionsRepository.findFirstByTraceId("trace-1")).thenReturn(Optional.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionId("txn-1")).thenReturn(List.of(debitDetail, creditDetail));
        when(walletRepository.findById(10L)).thenReturn(Optional.of(wallet(10L, "sub-1")));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(creditorWallet));
        when(walletBalanceRepository.lockBalance(20L)).thenReturn(creditorBalance);

        SettleTransactionRequest request = new SettleTransactionRequest();
        request.setTraceId("trace-1");
        request.setSettlementStatus(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.settleTransaction(request)
        );

        assertEquals("INSUFFICIENT_FIC_BALANCE", exception.getErrorCode());
    }

    private TransactionSettlementService service() {
        return new TransactionSettlementService(
                transactionsRepository,
                transactionDetailsRepository,
                walletRepository,
                walletBalanceRepository,
                walletLedgerRepository,
                transactionsService
        );
    }

    private Transactions ambiguousTransaction() {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-1");
        transaction.setTraceId("trace-1");
        transaction.setServiceCode("BILLPAY");
        transaction.setTransferStatus(Constants.TRANSACTION_AMBIGUOUS);
        transaction.setTransactionValue(new BigDecimal("1000.00"));
        return transaction;
    }

    private TransactionDetails debitDetail(String transactionId, String walletNumber) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, 1L));
        detail.setEntryType(Constants.TXN_TYPE_DR);
        detail.setWalletNumber(walletNumber);
        detail.setPreviousBalance(new BigDecimal("5000.00"));
        detail.setPostBalance(new BigDecimal("4000.00"));
        detail.setPreviousFicBalance(BigDecimal.ZERO);
        detail.setPostFicBalance(BigDecimal.ZERO);
        return detail;
    }

    private TransactionDetails creditDetail(String transactionId, String walletNumber) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, 2L));
        detail.setEntryType(Constants.TXN_TYPE_CR);
        detail.setWalletNumber(walletNumber);
        detail.setPreviousBalance(new BigDecimal("1000.00"));
        detail.setPostBalance(new BigDecimal("2000.00"));
        detail.setPreviousFicBalance(BigDecimal.ZERO);
        detail.setPostFicBalance(new BigDecimal("1000.00"));
        return detail;
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency("USD");
        return wallet;
    }

    private WalletBalance walletBalance(Long walletId, String availableBalance, String ficBalance) {
        WalletBalance balance = new WalletBalance();
        balance.setWalletId(walletId);
        balance.setAvailableBalance(new BigDecimal(availableBalance));
        balance.setFrozenBalance(BigDecimal.ZERO);
        balance.setFicBalance(new BigDecimal(ficBalance));
        return balance;
    }
}
