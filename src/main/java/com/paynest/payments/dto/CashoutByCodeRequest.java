package com.paynest.payments.dto;

import com.paynest.enums.RequestGateway;
import com.paynest.payments.enums.InitiatedBy;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CashoutByCodeRequest {
    private String operationType;
    private RequestGateway requestGateway;
    private String preferredLang;
    private InitiatedBy initiatedBy;
    private Party agent;
    private String msisdn;
    private String passcode;
    @Size(max = 100)
    private String paymentReference;
    @Size(max = 300)
    private String comments;
    private Map<String, Object> metadata;
    private Map<String, Object> additionalInfo;
}
