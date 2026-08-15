package com.paynest.payments.bulkpayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.payments.bulkpayment.enums.BatchDetailStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BatchDetailResponse {
    private Long batchDetailId;
    private String batchId;
    private String itemReference;
    private BatchDetailStatus status;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String creditorWalletType;
    private String creditorCurrency;
    private String creditorIdentifierType;
    private String creditorIdentifierValue;
    private String paymentReference;
    private String comments;
    private String validationErrorCode;
    private String validationErrorMessage;
    private String processingErrorCode;
    private String processingErrorMessage;
    private JsonNode additionalInfo;
}
