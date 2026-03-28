package com.paynest.payments.dto;

import com.paynest.enums.RequestGateway;
import com.paynest.payments.enums.InitiatedBy;

import java.util.Map;

public interface BasePaymentRequest {
    String getOperationType();
    RequestGateway getRequestGateway();
    String getPreferredLang();
    void setPreferredLang(String preferredLang);
    InitiatedBy getInitiatedBy();
    Party getDebitor();
    Party getCreditor();
    TransactionInfo getTransaction();
    String getPaymentReference();
    void setPaymentReference(String paymentReference);
    String getComments();
    void setComments(String comments);
    Map<String, Object> getMetadata();
    Map<String, Object> getAdditionalInfo();
}
