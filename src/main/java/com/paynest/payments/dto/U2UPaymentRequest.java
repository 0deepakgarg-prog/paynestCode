package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.enums.RequestGateway;
import com.paynest.payments.enums.InitiatedBy;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class U2UPaymentRequest implements BasePaymentRequest {

    private String operationType;

    private RequestGateway requestGateway;

    @JsonProperty("preferredLang")
    private String preferredLang;

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
