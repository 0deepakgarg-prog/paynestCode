package com.paynest.pricing.dto.response;

import lombok.Data;

import java.math.BigDecimal;

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

    public void addServiceCharge(BigDecimal amount) {
        serviceChargeAmount = serviceChargeAmount.add(defaultAmount(amount));
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

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String defaultParty(String existingValue, String newValue) {
        return existingValue != null ? existingValue : newValue;
    }
}
