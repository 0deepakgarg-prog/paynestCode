package com.paynest.payments.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.security.JWTUtils;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.BillPaymentSettlementRequest;
import com.paynest.payments.dto.BillPaymentSettlementResponse;
import com.paynest.payments.entity.BillPaymentStatusRecord;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillPaymentSettlementServiceTest {

    @Mock
    private BillPaymentStatusService billPaymentStatusService;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PropertyReader propertyReader;

    @Mock
    private PaymentTransactionRecorderService paymentTransactionRecorderService;

    @Mock
    private BalanceService balanceService;

    @Test
    void settle_shouldMarkBillPaymentSuccessfulWithoutMovingBalances() {
        BillPaymentSettlementService service = service();
        BillPaymentStatusRecord record = pendingRecord();
        Transactions transaction = successfulBillTransaction();

        when(billPaymentStatusService.getPendingRecord("BP240401-123456-A0001")).thenReturn(record);
        when(transactionsRepository.findFirstByTraceId("bill-trace-1")).thenReturn(Optional.of(transaction));

        BillPaymentSettlementRequest request = new BillPaymentSettlementRequest();
        request.setTraceId("bill-trace-1");
        request.setSettlementStatus(true);
        request.setComments("provider confirmed");
        request.setAdditionalInfo(Map.of("providerRef", "ELEC-1"));

        TraceContext.setTraceId("bill-settle-trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("ops-1");

            BillPaymentSettlementResponse response = service.settle(request);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("BILLPAY_SETTLE", response.getOperationType());
            assertEquals(BillPaymentStatus.SUCCESS, response.getBillStatus());
            assertEquals("BILL_SETTLEMENT_SUCCESS", response.getCode());
            assertEquals("BP240401-123456-A0001", response.getTransactionId());

            verify(billPaymentStatusService).markSuccess(
                    record,
                    "ops-1",
                    "provider confirmed",
                    Map.of("providerRef", "ELEC-1")
            );
            verify(paymentTransactionRecorderService).updateTransactionAdditionalInfo(
                    "BP240401-123456-A0001",
                    Map.of("providerRef", "ELEC-1")
            );
            verify(paymentTransactionRecorderService, never()).recordTransaction(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );
            verify(balanceService, never()).transferWalletAmount(any(), any(), any(), any(), any(), any());
            assertNotNull(transaction.getModifiedOn());
            assertNotNull(transaction.getTransferOn());
            assertEquals("sub-1", transaction.getModifiedBy());
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void settle_shouldRollbackFundsAndMarkBillPaymentFailed() {
        BillPaymentSettlementService service = service();
        BillPaymentStatusRecord record = pendingRecord();
        Transactions transaction = successfulBillTransaction();
        TransactionDetails debitDetail = debitDetail("BP240401-123456-A0001", "101", "SUBSCRIBER");
        TransactionDetails creditDetail = creditDetail("BP240401-123456-A0001", "202", "BILLER");
        Wallet customerWallet = wallet(101L, "sub-1");
        Wallet billerWallet = wallet(202L, "biller-1");
        Transactions rollbackTransaction = new Transactions();
        rollbackTransaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        rollbackTransaction.setModifiedBy("sub-1");

        when(billPaymentStatusService.getPendingRecord("BP240401-123456-A0001")).thenReturn(record);
        when(transactionsRepository.findFirstByTraceId("bill-trace-1")).thenReturn(Optional.of(transaction));
        when(transactionsRepository.findByTransactionId(anyString())).thenReturn(rollbackTransaction);
        when(transactionDetailsRepository.findByIdTransactionId("BP240401-123456-A0001"))
                .thenReturn(List.of(debitDetail, creditDetail));
        when(walletRepository.findById(101L)).thenReturn(Optional.of(customerWallet));
        when(walletRepository.findById(202L)).thenReturn(Optional.of(billerWallet));
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        BillPaymentSettlementRequest request = new BillPaymentSettlementRequest();
        request.setTraceId("bill-trace-1");
        request.setSettlementStatus(false);
        request.setComments("provider rejected");
        request.setAdditionalInfo(Map.of("providerRef", "ELEC-2"));

        TraceContext.setTraceId("bill-settle-trace-2");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("ops-1");

            BillPaymentSettlementResponse response = service.settle(request);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("BILLPAY_SETTLE", response.getOperationType());
            assertEquals(BillPaymentStatus.FAILED, response.getBillStatus());
            assertEquals("BILL_SETTLEMENT_FAILED_ROLLED_BACK", response.getCode());
            assertNotNull(response.getRollbackTransactionId());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> additionalInfoCaptor = ArgumentCaptor.forClass(Map.class);
            verify(paymentTransactionRecorderService).recordTransaction(
                    eq(response.getRollbackTransactionId()),
                    eq(new BigDecimal("10.50")),
                    eq("MOBILE"),
                    eq("BILLPAY_RB"),
                    eq("en"),
                    any(),
                    any(),
                    eq("BILLER"),
                    eq("SUBSCRIBER"),
                    eq(billerWallet),
                    eq(customerWallet),
                    eq(InitiatedBy.DEBITOR),
                    eq(null),
                    additionalInfoCaptor.capture(),
                    eq("pay-ref-1"),
                    eq("provider rejected")
            );
            assertEquals(1, additionalInfoCaptor.getValue().size());
            assertEquals("ELEC-2", additionalInfoCaptor.getValue().get("providerRef"));

            verify(balanceService).transferWalletAmount(
                    billerWallet,
                    customerWallet,
                    new BigDecimal("10.50"),
                    "BILLPAY_RB",
                    InitiatedBy.DEBITOR,
                    response.getRollbackTransactionId()
            );
            verify(billPaymentStatusService).markFailed(
                    record,
                    "ops-1",
                    "provider rejected",
                    Map.of("providerRef", "ELEC-2"),
                    response.getRollbackTransactionId()
            );
            verify(paymentTransactionRecorderService).updateTransactionAdditionalInfo(
                    "BP240401-123456-A0001",
                    Map.of("providerRef", "ELEC-2")
            );
            assertEquals(Constants.TRANSACTION_SUCCESS, transaction.getReconciliationDone());
            assertNotNull(transaction.getReconciliationDate());
            assertEquals("BP240401-123456-A0001", transaction.getReconciliationBy());
            assertNotNull(transaction.getModifiedOn());
            assertNotNull(transaction.getTransferOn());
            assertEquals("sub-1", transaction.getModifiedBy());
            assertEquals("transaction id", rollbackTransaction.getAttr1Name());
            assertEquals("BP240401-123456-A0001", rollbackTransaction.getAttr1Value());
            assertEquals("settlement status", rollbackTransaction.getAttr2Name());
            assertEquals("false", rollbackTransaction.getAttr2Value());
            assertEquals("sub-1", rollbackTransaction.getModifiedBy());
            assertNotNull(rollbackTransaction.getModifiedOn());
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void settle_shouldRejectAlreadySettledBillPayment() {
        BillPaymentSettlementService service = service();
        when(billPaymentStatusService.getPendingRecord("BP240401-123456-A0001"))
                .thenThrow(new ApplicationException("BILL_PAYMENT_ALREADY_SETTLED", "Already settled"));
        when(transactionsRepository.findFirstByTraceId("bill-trace-1"))
                .thenReturn(Optional.of(successfulBillTransaction()));

        BillPaymentSettlementRequest request = new BillPaymentSettlementRequest();
        request.setTraceId("bill-trace-1");
        request.setSettlementStatus(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.settle(request)
        );

        assertEquals("BILL_PAYMENT_ALREADY_SETTLED", exception.getErrorCode());
    }

    private BillPaymentSettlementService service() {
        return new BillPaymentSettlementService(
                billPaymentStatusService,
                transactionsRepository,
                transactionDetailsRepository,
                walletRepository,
                propertyReader,
                paymentTransactionRecorderService,
                balanceService
        );
    }

    private BillPaymentStatusRecord pendingRecord() {
        BillPaymentStatusRecord record = new BillPaymentStatusRecord();
        record.setTransactionId("BP240401-123456-A0001");
        record.setStatus(BillPaymentStatus.PENDING);
        record.setCustomerAccountId("sub-1");
        record.setBillerAccountId("biller-1");
        record.setTraceId("bill-trace-1");
        return record;
    }

    private Transactions successfulBillTransaction() {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("BP240401-123456-A0001");
        transaction.setTraceId("bill-trace-1");
        transaction.setServiceCode("BILLPAY");
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transaction.setTransactionValue(new BigDecimal("1050.00"));
        transaction.setRequestGateway("MOBILE");
        transaction.setLanguage("en");
        transaction.setPaymentReference("pay-ref-1");
        transaction.setDebitorAccountId("sub-1");
        transaction.setDebitorIdentifierType("MOBILE");
        transaction.setDebitorIdentifierValue("9999999999");
        transaction.setModifiedBy("sub-1");
        transaction.setCreditorAccountId("biller-1");
        transaction.setCreditorIdentifierType("LOGINID");
        transaction.setCreditorIdentifierValue("biller-login");
        return transaction;
    }

    private TransactionDetails debitDetail(String transactionId, String walletNumber, String userType) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, 1L));
        detail.setEntryType(Constants.TXN_TYPE_DR);
        detail.setWalletNumber(walletNumber);
        detail.setUserType(userType);
        return detail;
    }

    private TransactionDetails creditDetail(String transactionId, String walletNumber, String userType) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, 2L));
        detail.setEntryType(Constants.TXN_TYPE_CR);
        detail.setWalletNumber(walletNumber);
        detail.setUserType(userType);
        return detail;
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency("USD");
        return wallet;
    }
}
