package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;


@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class MerchpayPaymentResponse {
    private TransactionStatus responseStatus;

    private String operationType;

    private String code;

    private String message;

    private Instant timestamp;

    private String traceId;

    private String transactionId;

    private BigDecimal amount;

    private String currency;
}
