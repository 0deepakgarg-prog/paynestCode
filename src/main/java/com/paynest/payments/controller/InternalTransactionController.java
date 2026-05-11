package com.paynest.payments.controller;

import com.paynest.payments.dto.SettleTransactionRequest;
import com.paynest.payments.dto.SettleTransactionResponse;
import com.paynest.payments.service.TransactionSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalTransactionController {

    private final TransactionSettlementService transactionSettlementService;

    @PostMapping("/settletxn")
    public ResponseEntity<SettleTransactionResponse> settleTransaction(
            @RequestBody SettleTransactionRequest request) {
        SettleTransactionResponse response = transactionSettlementService.settleTransaction(request);
        return ResponseEntity.ok(response);
    }
}
