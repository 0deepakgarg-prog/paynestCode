package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.enums.RequestGateway;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class O2CPaymentRequest {

    private String operationType;

    @NotNull
    private RequestGateway requestGateway;

    @JsonProperty("preferredLang")
    private String preferredLang;

    @Size(max = 100)
    private String paymentReference;

    @Size(max = 300)
    private String comments;

    @NotNull
    private Party channel;

    @NotNull
    private TransactionInfo transaction;

    private Map<String, Object> metadata;

    private Map<String, Object> additionalInfo;
}
