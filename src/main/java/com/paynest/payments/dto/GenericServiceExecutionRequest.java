package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.enums.RequestGateway;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenericServiceExecutionRequest {

    @NotNull
    private RequestGateway requestGateway;

    @NotBlank
    private String language;

    private String serviceCode;

    private String referenceId;

    private Party debitor;

    private Party creditor;

    @JsonProperty("partner_data")
    @JsonAlias("partnerData")
    private JsonNode partnerData;

    @JsonAlias("financial_info")
    private GenericServiceFinancialInfo financialInfo;

    private JsonNode metadata;

    private JsonNode additionalInfo;
}
