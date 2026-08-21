package com.paynest.limits.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionLimitPeriodRequest {

    private Long limitPeriodId;

    @Size(max = 20, message = "periodType must not exceed 20 characters")
    private String periodType;

    @Min(value = 0, message = "maxCount must be zero or greater")
    private Integer maxCount;

    private BigDecimal maxAmount;

    @Size(max = 20, message = "status must not exceed 20 characters")
    private String status;
}
