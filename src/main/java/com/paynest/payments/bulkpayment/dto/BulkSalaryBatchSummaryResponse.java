package com.paynest.payments.bulkpayment.dto;

import com.paynest.payments.bulkpayment.enums.BatchStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BulkSalaryBatchSummaryResponse {
    private String batchId;
    private String batchReference;
    private String batchType;
    private BatchStatus status;
    private String transactionId;
    private int totalRecords;
    private int validRecords;
    private int failedRecords;
    private BigDecimal totalAmount;
    private String currency;
    private String debitorAccountId;
    private String debitorWalletType;
    private String debitorCurrency;
    private LocalDateTime createdOn;
    private LocalDateTime modifiedOn;
    private String message;
}
