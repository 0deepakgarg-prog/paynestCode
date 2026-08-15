package com.paynest.payments.controller;

import com.paynest.payments.dto.CardPreAuthAdjustmentRequest;
import com.paynest.payments.dto.CardPreAuthDebitRequest;
import com.paynest.payments.dto.CardPreAuthHoldRequest;
import com.paynest.payments.dto.CardPreAuthHoldResponse;
import com.paynest.payments.service.CardPreAuthHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/card/preauth")
@RequiredArgsConstructor
public class CardPreAuthHoldController {

    private final CardPreAuthHoldService cardPreAuthHoldService;

    @PostMapping("/holds")
    public ResponseEntity<CardPreAuthHoldResponse> createHold(
            @Valid @RequestBody CardPreAuthHoldRequest request) {
        return ResponseEntity.ok(cardPreAuthHoldService.createHold(request));
    }

    @PostMapping("/holds/{holdId}/increment")
    public ResponseEntity<CardPreAuthHoldResponse> incrementHold(
            @PathVariable String holdId,
            @Valid @RequestBody CardPreAuthAdjustmentRequest request) {
        return ResponseEntity.ok(cardPreAuthHoldService.incrementHold(holdId, request));
    }

    @PostMapping("/holds/{holdId}/decrement")
    public ResponseEntity<CardPreAuthHoldResponse> decrementHold(
            @PathVariable String holdId,
            @Valid @RequestBody CardPreAuthAdjustmentRequest request) {
        return ResponseEntity.ok(cardPreAuthHoldService.decrementHold(holdId, request));
    }

    @PostMapping("/holds/{holdId}/debit")
    public ResponseEntity<CardPreAuthHoldResponse> debitHold(
            @PathVariable String holdId,
            @Valid @RequestBody CardPreAuthDebitRequest request) {
        return ResponseEntity.ok(cardPreAuthHoldService.debitHold(holdId, request));
    }
}
