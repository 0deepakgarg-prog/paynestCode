package com.paynest.pricing.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.pricing.entity.PricingRule;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PricingRuleResponse {

    private Long id;
    private String pricingName;
    private String serviceCode;
    private String ruleType;
    private String pricingType;
    private String payer;
    private String payBy;
    private JsonNode payerSplit;
    private String senderTagKey;
    private String receiverTagKey;
    private String currency;
    private JsonNode pricingConfig;
    private String status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public PricingRuleResponse(PricingRule pricingRule) {
        this.id = pricingRule.getId();
        this.pricingName = pricingRule.getPricingName();
        this.serviceCode = pricingRule.getServiceCode();
        this.ruleType = pricingRule.getRuleType();
        this.pricingType = pricingRule.getPricingType();
        this.payer = pricingRule.getPayer();
        this.payBy = pricingRule.getPayBy();
        this.payerSplit = pricingRule.getPayerSplit();
        this.senderTagKey = pricingRule.getSenderTagKey();
        this.receiverTagKey = pricingRule.getReceiverTagKey();
        this.currency = pricingRule.getCurrency();
        this.pricingConfig = pricingRule.getPricingConfig();
        this.status = pricingRule.getStatus();
        this.validFrom = pricingRule.getValidFrom();
        this.validTo = pricingRule.getValidTo();
        this.createdAt = pricingRule.getCreatedAt();
        this.updatedAt = pricingRule.getUpdatedAt();
        this.createdBy = pricingRule.getCreatedBy();
        this.updatedBy = pricingRule.getUpdatedBy();
    }
}
