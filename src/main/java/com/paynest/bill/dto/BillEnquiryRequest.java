package com.paynest.bill.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public class BillEnquiryRequest {

    @NotBlank
    @JsonProperty("biller_code")
    private String billerCode;

    @JsonProperty("partner_data")
    private JsonNode partnerData;

    public String getBillerCode() {
        return billerCode;
    }

    public void setBillerCode(String billerCode) {
        this.billerCode = billerCode;
    }

    public JsonNode getPartnerData() {
        return partnerData;
    }

    public void setPartnerData(JsonNode partnerData) {
        this.partnerData = partnerData;
    }
}
