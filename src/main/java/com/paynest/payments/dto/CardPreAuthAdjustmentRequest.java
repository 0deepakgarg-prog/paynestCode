package com.paynest.payments.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CardPreAuthAdjustmentRequest {
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal amount;

    private String comments;

    private Map<String, Object> additionalInfo;
}
