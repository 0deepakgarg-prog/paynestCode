package com.paynest.payments.qr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.enums.RequestGateway;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.Party;
import com.paynest.payments.enums.InitiatedBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class QrPayRequest {

    private String payload;

    private String qrIntentId;

    @NotNull
    private RequestGateway requestGateway;

    @JsonProperty("preferredLang")
    private String preferredLang;

    private InitiatedBy initiatedBy = InitiatedBy.DEBITOR;

    @Valid
    @NotNull
    private Party debitor;

    @Positive
    private BigDecimal amount;

    private String currency;

    @Valid
    private Authentication authentication;

    @Size(max = 100)
    private String paymentReference;

    @Size(max = 300)
    private String comments;

    private Map<String, Object> metadata;

    private Map<String, Object> additionalInfo;
}
