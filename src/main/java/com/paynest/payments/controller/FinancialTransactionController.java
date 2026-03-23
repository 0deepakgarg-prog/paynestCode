package com.paynest.payments.controller;

import com.paynest.payments.dto.StockApprovalRequest;
import com.paynest.payments.dto.StockInitiateRequest;
import com.paynest.payments.dto.StockReimbursementInitiateRequest;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.service.StockService;
import com.paynest.payments.service.U2UPaymentService;
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
    private final StockService stockService;

    @PostMapping("/U2U")
    public ResponseEntity<BasePaymentResponse> transferMoney(
            @Valid @RequestBody U2UPaymentRequest request) {

        BasePaymentResponse response = u2uPaymentService.processPayment(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockInitiate")
    public ResponseEntity<BasePaymentResponse> initiateStock(
            @Valid @RequestBody StockInitiateRequest request) {

        BasePaymentResponse response = stockService.initiateStock(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockStatusUpdate")
    public ResponseEntity<BasePaymentResponse> updateStockStatus(
            @Valid @RequestBody StockApprovalRequest request) {

        BasePaymentResponse response = stockService.updateStockTransactionStatus(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockReimbursementInitiate")
    public ResponseEntity<BasePaymentResponse> initiateStockReimbursement(
            @Valid @RequestBody StockReimbursementInitiateRequest request) {

        BasePaymentResponse response = stockService.initiateStockReimbursement(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockReimbursementStatusUpdate")
    public ResponseEntity<BasePaymentResponse> updateStockReimbursementStatus(
            @Valid @RequestBody StockApprovalRequest request) {

        BasePaymentResponse response = stockService.updateStockReimbursementTransactionStatus(request);

        return ResponseEntity.ok(response);
    }

}

