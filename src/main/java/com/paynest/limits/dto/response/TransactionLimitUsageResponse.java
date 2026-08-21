package com.paynest.limits.dto.response;

import com.paynest.limits.entity.TransactionLimitUsage;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionLimitUsageResponse {

    private Long usageId;
    private String subjectKey;
    private String subjectValue;
    private String accountId;
    private Long limitId;
    private String limitName;
    private Long limitDetailsId;
    private Long limitPeriodId;
    private Long tagId;
    private String tagCode;
    private String partyType;
    private String walletType;
    private String currency;
    private String periodType;
    private String operationType;
    private String requestGateway;
    private Integer usedCount;
    private Integer maxCount;
    private Integer remainingCount;
    private BigDecimal usedAmount;
    private BigDecimal maxAmount;
    private BigDecimal remainingAmount;
    private String lastTransactionId;
    private LocalDateTime lastTransactionDate;

    public TransactionLimitUsageResponse() {
    }

    public TransactionLimitUsageResponse(TransactionLimitUsage usage) {
        this.usageId = usage.getUsageId();
        this.subjectKey = usage.getSubjectKey();
        this.subjectValue = usage.getSubjectValue();
        this.accountId = usage.getAccountId();
        this.limitId = usage.getLimitId();
        this.limitDetailsId = usage.getLimitDetailsId();
        this.tagId = usage.getTagId();
        this.periodType = usage.getPeriodType();
        this.operationType = usage.getOperationType();
        this.requestGateway = usage.getRequestGateway();
        this.lastTransactionId = usage.getLastTransactionId();
        this.lastTransactionDate = usage.getLastTransactionDate();
    }
}
