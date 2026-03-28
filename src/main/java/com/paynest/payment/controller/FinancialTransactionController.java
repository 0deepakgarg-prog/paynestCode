package com.paynest.payment.controller;

import com.paynest.payments.dto.BillPayPaymentRequest;
import com.paynest.payments.dto.BillPayPaymentResponse;
import com.paynest.payments.dto.CashInPaymentRequest;
import com.paynest.payments.dto.CashInPaymentResponse;
import com.paynest.payments.dto.CashOutPaymentRequest;
import com.paynest.payments.dto.CashOutPaymentResponse;
import com.paynest.payments.dto.MerchpayPaymentRequest;
import com.paynest.payments.dto.MerchpayPaymentResponse;
import com.paynest.payments.dto.SettleTransactionRequest;
import com.paynest.payments.dto.SettleTransactionResponse;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.dto.U2UPaymentResponse;
import com.paynest.payment.service.BillPayPaymentService;
import com.paynest.payment.service.CashInPaymentService;
import com.paynest.payment.service.CashOutPaymentService;
import com.paynest.payment.service.MerchPayPaymentService;
import com.paynest.payment.service.TransactionSettlementService;
import com.paynest.payment.service.U2UPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class FinancialTransactionController {
    private final U2UPaymentService u2uPaymentService;
    private final MerchPayPaymentService merchpayPaymentService;
    private final CashInPaymentService cashInPaymentService;
    private final CashOutPaymentService cashOutPaymentService;
    private final BillPayPaymentService billPayPaymentService;
    private final TransactionSettlementService transactionSettlementService;

    @PostMapping("/U2U")
    public ResponseEntity<U2UPaymentResponse> transferU2UMoney(
            @Valid @RequestBody U2UPaymentRequest request) {
        request.setOperationType("U2U");
        U2UPaymentResponse response = u2uPaymentService.processPayment(request, true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/MERCHANTPAY")
    public ResponseEntity<MerchpayPaymentResponse> transferMERCHPAYMoney(
            @Valid @RequestBody MerchpayPaymentRequest request) {
        request.setOperationType("MERCHANTPAY");
        MerchpayPaymentResponse response = merchpayPaymentService.processPayment(request, true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/CASHIN")
    public ResponseEntity<CashInPaymentResponse> transferCashIn(
            @Valid @RequestBody CashInPaymentRequest request) {
        request.setOperationType("CASHIN");
        CashInPaymentResponse response = cashInPaymentService.processPayment(request, true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/CASHOUT")
    public ResponseEntity<CashOutPaymentResponse> transferCashOut(
            @Valid @RequestBody CashOutPaymentRequest request) {
        request.setOperationType("CASHOUT");
        CashOutPaymentResponse response = cashOutPaymentService.processPayment(request, true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/BILLPAY")
    public ResponseEntity<BillPayPaymentResponse> transferBillPayment(
            @Valid @RequestBody BillPayPaymentRequest request) {
        request.setOperationType("BILLPAY");
        BillPayPaymentResponse response = billPayPaymentService.processPayment(request, true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/settleTxn")
    public ResponseEntity<SettleTransactionResponse> settleTransaction(
            @RequestBody SettleTransactionRequest request) {
        SettleTransactionResponse response = transactionSettlementService.settleTransaction(request);
        return ResponseEntity.ok(response);
    }

}
