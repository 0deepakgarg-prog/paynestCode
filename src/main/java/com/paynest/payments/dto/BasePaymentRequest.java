package com.paynest.payments.dto;

import com.paynest.payments.enums.InitiatedBy;

public interface BasePaymentRequest {
    String getOperationType();
    InitiatedBy getInitiatedBy();
    Party getDebitor();
    Party getCreditor();
    TransactionInfo getTransaction();
}

