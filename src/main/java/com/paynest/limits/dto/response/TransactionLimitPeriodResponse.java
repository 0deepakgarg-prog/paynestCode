package com.paynest.limits.dto.response;

import com.paynest.limits.entity.TransactionLimitProfilePeriod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionLimitPeriodResponse {

    private Long limitPeriodId;
    private Long limitDetailsId;
    private String periodType;
    private Integer maxCount;
    private BigDecimal maxAmount;
    private String status;
    private LocalDateTime createdOn;
    private LocalDateTime modifiedOn;

    public TransactionLimitPeriodResponse(TransactionLimitProfilePeriod period) {
        this.limitPeriodId = period.getLimitPeriodId();
        this.limitDetailsId = period.getLimitDetailsId();
        this.periodType = period.getPeriodType();
        this.maxCount = period.getMaxCount();
        this.maxAmount = period.getMaxAmount();
        this.status = period.getStatus();
        this.createdOn = period.getCreatedOn();
        this.modifiedOn = period.getModifiedOn();
    }
}
