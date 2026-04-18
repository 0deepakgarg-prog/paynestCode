package com.paynest.payments.controller;

import com.paynest.payments.dto.PaymentHistoryResponse;
import com.paynest.payments.service.PaymentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final PaymentHistoryService paymentHistoryService;

    @GetMapping("/history")
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
