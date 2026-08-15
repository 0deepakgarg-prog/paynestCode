package com.paynest.payments.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CardPreAuthDebitRequest {
    @NotNull
    private Party debitor;

    @NotNull
    private Party creditor;

    @NotNull
    private TransactionInfo transaction;

    private String paymentReference;

    private String comments;

    private Map<String, Object> additionalInfo;
}
