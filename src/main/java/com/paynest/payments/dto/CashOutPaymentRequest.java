package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.enums.RequestGateway;
import com.paynest.payments.enums.InitiatedBy;
import lombok.Data;

import java.util.Map;

@Data
public class CashOutPaymentRequest implements BasePaymentRequest {

    private String operationType;

    private RequestGateway requestGateway;

    @JsonProperty("preferredLang")
    private String preferredLang;

    private InitiatedBy initiatedBy;

    private String paymentReference;

    private String comments;

    private Party debitor;

    private Party creditor;

    private TransactionInfo transaction;

    private Map<String, Object> metadata;

    private Map<String, Object> additionalInfo;
}
