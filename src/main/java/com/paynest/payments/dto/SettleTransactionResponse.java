package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class SettleTransactionResponse {
    private TransactionStatus responseStatus;

    private String operationType;

    private String code;

    private String message;

    private Instant timestamp;

    private String traceId;

    private String transactionId;

    private String transactionTraceId;

    private String serviceCode;

    private String transferStatus;
}
