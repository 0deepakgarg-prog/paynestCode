package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.enums.RequestGateway;
import com.paynest.payments.enums.InitiatedBy;
import lombok.Data;

import java.util.Map;

@Data
public class BillPayPaymentRequest implements BasePaymentRequest {

    private String operationType;

    private RequestGateway requestGateway;

    @JsonProperty("preferredLang")
    private String preferredLang;

    private InitiatedBy initiatedBy;

    private String paymentReference;

    private String comments;

    private Party debitor;

    private Party creditor;

    private TransactionInfo transaction;

    private Map<String, Object> metadata;

    @JsonProperty("partner_data")
    @JsonAlias("additionalInfo")
    private Map<String, Object> partnerData;

    @Override
    public Map<String, Object> getAdditionalInfo() {
        return partnerData;
    }

    public void setAdditionalInfo(Map<String, Object> additionalInfo) {
        this.partnerData = additionalInfo;
    }
}
