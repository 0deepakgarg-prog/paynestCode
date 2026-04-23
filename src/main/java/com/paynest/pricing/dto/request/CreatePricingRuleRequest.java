package com.paynest.pricing.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatePricingRuleRequest {

    @NotBlank(message = "Pricing name is required")
    @Size(max = 100, message = "Pricing name must not exceed 100 characters")
    private String pricingName;

    @NotBlank(message = "Service code is required")
    @Size(max = 50, message = "Service code must not exceed 50 characters")
    private String serviceCode;

    @NotBlank(message = "Rule type is required")
    @Size(max = 30, message = "Rule type must not exceed 30 characters")
    private String ruleType;

    @Size(max = 20, message = "Pricing type must not exceed 20 characters")
    private String pricingType;

    @NotBlank(message = "Payer is required")
    @Size(max = 20, message = "Payer must not exceed 20 characters")
    private String payer;

    @Size(max = 20, message = "Pay by must not exceed 20 characters")
    private String payBy;

    private JsonNode payerSplit;

    @NotBlank(message = "Sender tag key is required")
    @Size(max = 255, message = "Sender tag key must not exceed 255 characters")
    private String senderTagKey;

    @NotBlank(message = "Receiver tag key is required")
    @Size(max = 255, message = "Receiver tag key must not exceed 255 characters")
    private String receiverTagKey;

    @NotBlank(message = "Currency is required")
    @Size(max = 10, message = "Currency must not exceed 10 characters")
    private String currency;

    @NotNull(message = "Pricing config is required")
    private JsonNode pricingConfig;

    @Size(max = 50, message = "Status must not exceed 50 characters")
    private String status;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;
}
