package com.paynest.bill.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class BillEnquiryCamelRequest {

    @JsonProperty("biller_code")
    private String billerCode;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("partner_data")
    private JsonNode partnerData;

    public BillEnquiryCamelRequest(String billerCode, String tenantId, JsonNode partnerData) {
        this.billerCode = billerCode;
        this.tenantId = tenantId;
        this.partnerData = partnerData;
    }

    public String getBillerCode() {
        return billerCode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public JsonNode getPartnerData() {
        return partnerData;
    }
}
