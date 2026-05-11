package com.paynest.pricing.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdatePricingRuleRequest {

    @Size(max = 100, message = "Pricing name must not exceed 100 characters")
    private String pricingName;

    @Size(max = 20, message = "Payer must not exceed 20 characters")
    private String payer;

    @Size(max = 20, message = "Pay by must not exceed 20 characters")
    private String payBy;

    private JsonNode payerSplit;

    private JsonNode pricingConfig;

    @Size(max = 50, message = "Status must not exceed 50 characters")
    private String status;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;
}
