package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class BillPaymentSettlementResponse {
    private TransactionStatus responseStatus;
    private String operationType;
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String traceId;
    private String transactionId;
    private String rollbackTransactionId;
    private BillPaymentStatus billStatus;
}
