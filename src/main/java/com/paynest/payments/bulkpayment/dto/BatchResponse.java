package com.paynest.payments.bulkpayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.payments.bulkpayment.enums.BatchStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BatchResponse {
    private String batchId;
    private String batchReference;
    private String batchType;
    private BatchStatus status;
    private String transactionId;
    private Integer totalRecords;
    private Integer validRecords;
    private Integer failedRecords;
    private BigDecimal totalAmount;
    private String currency;
    private String debitorAccountId;
    private String debitorWalletType;
    private String debitorCurrency;
    private String createdBy;
    private String approvedBy;
    private String rejectedBy;
    private LocalDateTime validationStartedOn;
    private LocalDateTime validationCompletedOn;
    private LocalDateTime approvedOn;
    private LocalDateTime rejectedOn;
    private LocalDateTime processingStartedOn;
    private LocalDateTime processingCompletedOn;
    private String failureReason;
    private String remarks;
    private JsonNode additionalInfo;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
}
