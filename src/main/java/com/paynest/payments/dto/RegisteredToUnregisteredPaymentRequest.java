package com.paynest.payments.dto;

import com.paynest.enums.RequestGateway;
import com.paynest.payments.enums.InitiatedBy;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class RegisteredToUnregisteredPaymentRequest {
    private String operationType;
    private RequestGateway requestGateway;
    private String preferredLang;
    private InitiatedBy initiatedBy;
    private Party debitor;
    private String receiverMsisdn;
    private String receiverFirstName;
    private String receiverLastName;
    private String receiverKycDocumentId;
    private TransactionInfo transaction;
    @Size(max = 100)
    private String paymentReference;
    @Size(max = 300)
    private String comments;
    private Map<String, Object> metadata;
    private Map<String, Object> additionalInfo;
    private String field1;
    private String field2;
    private String field3;
    private String field4;
    private String field5;
}
