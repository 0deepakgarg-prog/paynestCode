package com.paynest.payments.bulkpayment.enums;

public enum BatchStatus {
    VALIDATION_INITIATED,
    VALIDATION_IN_PROGRESS,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    PROCESSING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    REFUNDED
}
