package com.paynest.payments.entity;

import com.paynest.payments.enums.BillPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "bill_payment_status")
@Data
public class BillPaymentStatusRecord {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillPaymentStatus status;

    @Column(name = "customer_account_id", nullable = false)
    private String customerAccountId;

    @Column(name = "biller_account_id", nullable = false)
    private String billerAccountId;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "comments")
    private String comments;

    @Column(name = "additional_info")
    private String additionalInfo;

    @Column(name = "rollback_transaction_id")
    private String rollbackTransactionId;

    @Column(name = "settled_by")
    private String settledBy;

    @Column(name = "settled_on")
    private LocalDateTime settledOn;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;
}
