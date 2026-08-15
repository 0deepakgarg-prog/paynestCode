package com.paynest.payments.bulkpayment.controller;

import com.paynest.payments.bulkpayment.dto.BulkSalaryBatchActionRequest;
import com.paynest.payments.bulkpayment.dto.BulkSalaryBatchRefundRequest;
import com.paynest.payments.bulkpayment.dto.BulkSalaryBatchSummaryResponse;
import com.paynest.payments.bulkpayment.dto.BulkSalaryPaymentRequest;
import com.paynest.payments.bulkpayment.service.BulkSalaryBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bulk-payments/salary/batches")
@RequiredArgsConstructor
public class BulkSalaryBatchController {

    private final BulkSalaryBatchService bulkSalaryBatchService;

    @PostMapping
    public ResponseEntity<BulkSalaryBatchSummaryResponse> createBatch(
            @Valid @RequestBody BulkSalaryPaymentRequest request) {
        return ResponseEntity.ok(bulkSalaryBatchService.createBatch(request));
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<BulkSalaryBatchSummaryResponse> getBatch(
            @PathVariable String batchId) {
        return ResponseEntity.ok(bulkSalaryBatchService.getBatchSummary(batchId));
    }

    @PostMapping("/{batchId}/approve")
    public ResponseEntity<BulkSalaryBatchSummaryResponse> approveBatch(
            @PathVariable String batchId,
            @Valid @RequestBody BulkSalaryBatchActionRequest request) {
        return ResponseEntity.ok(bulkSalaryBatchService.approveBatch(batchId, request));
    }

    @PostMapping("/{batchId}/start-validation")
    public ResponseEntity<BulkSalaryBatchSummaryResponse> startValidation(
            @PathVariable String batchId) {
        return ResponseEntity.ok(bulkSalaryBatchService.startValidation(batchId));
    }

    @PostMapping("/{batchId}/reject")
    public ResponseEntity<BulkSalaryBatchSummaryResponse> rejectBatch(
            @PathVariable String batchId,
            @Valid @RequestBody BulkSalaryBatchActionRequest request) {
        return ResponseEntity.ok(bulkSalaryBatchService.rejectBatch(batchId, request));
    }

    @PostMapping("/{batchId}/retry-failed")
    public ResponseEntity<BulkSalaryBatchSummaryResponse> retryFailed(
            @PathVariable String batchId) {
        return ResponseEntity.ok(bulkSalaryBatchService.retryFailed(batchId));
    }

    @PostMapping("/{batchId}/refund-failed")
    public ResponseEntity<BulkSalaryBatchSummaryResponse> refundFailed(
            @PathVariable String batchId,
            @Valid @RequestBody BulkSalaryBatchRefundRequest request) {
        return ResponseEntity.ok(bulkSalaryBatchService.refundFailed(batchId, request));
    }
}
