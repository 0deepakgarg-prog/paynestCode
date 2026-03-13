package com.paynest.payment.controller;

import com.paynest.payment.dto.U2UPaymentRequest;
import com.paynest.payment.dto.U2UPaymentResponse;
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

    @PostMapping("/U2U")
    public ResponseEntity<U2UPaymentResponse> transferMoney(
            @Valid @RequestBody U2UPaymentRequest request) {

        U2UPaymentResponse response = u2uPaymentService.processPayment(request);

        return ResponseEntity.ok(response);
    }
}
