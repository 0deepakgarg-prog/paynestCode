package com.paynest.pricing.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PricingComputationResponse {

    private String senderTagKey;
    private String receiverTagKey;
    private String serviceChargeAffectedParty;
    private String commissionAffectedParty;
    private String discountAffectedParty;
    private String cashbackAffectedParty;
    private BigDecimal serviceChargeAmount = BigDecimal.ZERO;
    private BigDecimal commissionAmount = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal cashbackAmount = BigDecimal.ZERO;
    private List<PricingRuleDetails> serviceChargeRules = new ArrayList<>();
    private List<PricingRuleDetails> commissionRules = new ArrayList<>();
    private List<PricingRuleDetails> discountRules = new ArrayList<>();
    private List<PricingRuleDetails> cashbackRules = new ArrayList<>();
    private String cashbackPayBy;

    public void addServiceCharge(BigDecimal amount) {
        serviceChargeAmount = serviceChargeAmount.add(defaultAmount(amount));
    }

    public void addServiceChargeRule(PricingRuleDetails ruleDetails) {
        if (ruleDetails != null) {
            serviceChargeRules.add(ruleDetails);
        }
    }

    public void addDiscountRule(PricingRuleDetails ruleDetails) {
        if (ruleDetails != null) {
            discountRules.add(ruleDetails);
        }
    }

    public void addCommissionRule(PricingRuleDetails ruleDetails) {
        if (ruleDetails != null) {
            commissionRules.add(ruleDetails);
        }
    }

    public void addCashbackRule(PricingRuleDetails ruleDetails) {
        if (ruleDetails != null) {
            cashbackRules.add(ruleDetails);
        }
    }

    public void addCommission(BigDecimal amount) {
        commissionAmount = commissionAmount.add(defaultAmount(amount));
    }

    public void addDiscount(BigDecimal amount) {
        discountAmount = discountAmount.add(defaultAmount(amount));
    }

    public void addCashback(BigDecimal amount) {
        cashbackAmount = cashbackAmount.add(defaultAmount(amount));
    }

    public void markServiceChargeAffectedParty(String affectedParty) {
        serviceChargeAffectedParty = defaultParty(serviceChargeAffectedParty, affectedParty);
    }

    public void markCommissionAffectedParty(String affectedParty) {
        commissionAffectedParty = defaultParty(commissionAffectedParty, affectedParty);
    }

    public void markDiscountAffectedParty(String affectedParty) {
        discountAffectedParty = defaultParty(discountAffectedParty, affectedParty);
    }

    public void markCashbackAffectedParty(String affectedParty) {
        cashbackAffectedParty = defaultParty(cashbackAffectedParty, affectedParty);
    }

    public void markCashbackPayBy(String payBy) {
        cashbackPayBy = defaultParty(cashbackPayBy, payBy);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String defaultParty(String existingValue, String newValue) {
        return existingValue != null ? existingValue : newValue;
    }

    @Data
    public static class PricingRuleDetails {
        private Long id;
        private String pricingName;
        private String serviceCode;
        private String ruleType;
        private String pricingType;
        private String payer;
        private String payBy;
        private String senderTagKey;
        private String receiverTagKey;
        private String currency;
        private JsonNode pricingConfig;
        private BigDecimal calculatedAmount;
    }
}
