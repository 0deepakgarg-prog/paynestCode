package com.paynest.payment.dto;

import com.paynest.enums.InitiatedBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Data
public class U2UPaymentRequest implements BasePaymentRequest {

    private String operationType;

    private InitiatedBy initiatedBy;

    @Size(max = 100)
    private String paymentReference;

    @Size(max = 300)
    private String comments;

    private Party debitor;

    private Party creditor;

    private TransactionInfo transaction;

    private Map<String, Object> metadata;

    private Map<String, Object> additionalInfo;
}
