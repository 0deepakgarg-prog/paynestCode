package com.paynest.payments.controller;

import com.paynest.payments.dto.TransactionDetailResponse;
import com.paynest.payments.service.TransactionDetailQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transaction")
@RequiredArgsConstructor
public class TransactionDetailController {

    private final TransactionDetailQueryService transactionDetailQueryService;

    @GetMapping("/{accountId}/{transactionId}")
    public ResponseEntity<TransactionDetailResponse> getTransactionDetail(
            @PathVariable String accountId,
            @PathVariable String transactionId
    ) {
        return ResponseEntity.ok(transactionDetailQueryService.getTransactionDetail(accountId, transactionId));
    }
}
