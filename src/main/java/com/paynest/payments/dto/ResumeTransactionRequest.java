package com.paynest.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ResumeTransactionRequest {

    @Size(max = 300)
    private String comments;

    @NotNull
    private TransactionInfo transaction;

    private Map<String, Object> metadata;

    private Map<String, Object> additionalInfo;
}

