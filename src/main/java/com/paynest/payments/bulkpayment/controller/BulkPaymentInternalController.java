package com.paynest.payments.bulkpayment.controller;

import com.paynest.payments.bulkpayment.dto.SalaryPaymentInternalRequest;
import com.paynest.payments.bulkpayment.service.BulkPaymentInternalPaymentService;
import com.paynest.payments.dto.BasePaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/bulk-payments")
@RequiredArgsConstructor
public class BulkPaymentInternalController {

    private final BulkPaymentInternalPaymentService bulkPaymentInternalPaymentService;

    @PostMapping("/SALPAY")
    public ResponseEntity<BasePaymentResponse> paySalary(
            @Valid @RequestBody SalaryPaymentInternalRequest request) {
        return ResponseEntity.ok(bulkPaymentInternalPaymentService.paySalary(request));
    }
}
