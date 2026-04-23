package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class BillPayPaymentResponse {
    private TransactionStatus responseStatus;

    private String operationType;

    private String code;

    private String message;

    private Instant timestamp;

    private String traceId;

    private String transactionId;

    private BigDecimal amount;

    private String currency;

    private BillPaymentStatus billStatus;
}
