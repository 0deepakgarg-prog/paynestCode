package com.paynest.payments.controller;

import com.paynest.payments.dto.PaymentHistoryResponse;
import com.paynest.payments.dto.TransactionDetailResponse;
import com.paynest.payments.service.PaymentHistoryService;
import com.paynest.payments.service.TransactionDetailQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransactionDetailsController {

    private final TransactionDetailQueryService transactionDetailQueryService;
    private final PaymentHistoryService paymentHistoryService;

    @GetMapping("/api/v1/transaction/{accountId}/{transactionId}")
    public ResponseEntity<TransactionDetailResponse> getTransactionDetail(
            @PathVariable String accountId,
            @PathVariable String transactionId
    ) {
        return ResponseEntity.ok(transactionDetailQueryService.getTransactionDetail(accountId, transactionId));
    }

    @GetMapping({
            "/api/v1/transaction/history",
            "/api/v1/payment/history"
    })
    public ResponseEntity<PaymentHistoryResponse> getPaymentHistory(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String paymentMethodType,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(paymentHistoryService.getPaymentHistory(
                accountId,
                fromDate,
                toDate,
                offset,
                limit,
                paymentMethodType,
                order,
                status
        ));
    }
}
