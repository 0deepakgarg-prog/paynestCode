package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GenericIntegratorPayload {

    private String serviceCode;

    private String serviceName;

    private String serviceCategory;

    private String transactionType;

    private String serviceType;

    private String referenceId;

    private String transactionId;

    private GenericServiceParty debitor;

    private GenericServiceParty creditor;

    @JsonProperty("partner_data")
    private JsonNode partnerData;

    private GenericServiceFinancialInfo financialInfo;

    private PricingComputationResponse pricingInfo;

    private JsonNode metadata;

    private JsonNode additionalInfo;
}
