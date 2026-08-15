package com.paynest.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CardPreAuthHoldRequest {
    @NotBlank
    private String cmsTransactionId;

    @NotNull
    private Party debitor;

    @NotNull
    private TransactionInfo transaction;

    private String cmsReference;

    private String merchantId;

    private String comments;

    private Map<String, Object> additionalInfo;
}
