package com.paynest.payment.dto;

import com.paynest.enums.InitiatedBy;

public interface BasePaymentRequest {
    String getOperationType();
    InitiatedBy getInitiatedBy();
    Party getDebitor();
    Party getCreditor();
    TransactionInfo getTransaction();
}
