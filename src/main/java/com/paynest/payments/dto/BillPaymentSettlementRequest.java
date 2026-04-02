package com.paynest.payments.dto;

import lombok.Data;

import java.util.Map;

@Data
public class BillPaymentSettlementRequest {
    private String traceId;
    private Boolean settlementStatus;
    private String comments;
    private Map<String, Object> additionalInfo;
}
