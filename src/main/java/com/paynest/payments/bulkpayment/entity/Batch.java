package com.paynest.payments.bulkpayment.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.config.tenant.TenantTime;
import com.paynest.payments.bulkpayment.enums.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "batches")
@Data
public class Batch {

    @Id
    @Column(name = "batch_id", nullable = false, length = 30)
    private String batchId;

    @Column(name = "batch_reference", length = 100)
    private String batchReference;

    @Column(name = "batch_type", nullable = false, length = 30)
    private String batchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BatchStatus status;

    @Column(name = "transaction_id", length = 30)
    private String transactionId;

    @Column(name = "total_records", nullable = false)
    private Integer totalRecords;

    @Column(name = "valid_records", nullable = false)
    private Integer validRecords;

    @Column(name = "failed_records", nullable = false)
    private Integer failedRecords;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "debitor_account_id", length = 30)
    private String debitorAccountId;

    @Column(name = "debitor_wallet_type", length = 50)
    private String debitorWalletType;

    @Column(name = "debitor_currency", length = 10)
    private String debitorCurrency;

    @Column(name = "created_by", length = 30)
    private String createdBy;

    @Column(name = "approved_by", length = 30)
    private String approvedBy;

    @Column(name = "rejected_by", length = 30)
    private String rejectedBy;

    @Column(name = "validation_started_on")
    private LocalDateTime validationStartedOn;

    @Column(name = "validation_completed_on")
    private LocalDateTime validationCompletedOn;

    @Column(name = "approved_on")
    private LocalDateTime approvedOn;

    @Column(name = "rejected_on")
    private LocalDateTime rejectedOn;

    @Column(name = "processing_started_on")
    private LocalDateTime processingStartedOn;

    @Column(name = "processing_completed_on")
    private LocalDateTime processingCompletedOn;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_info", columnDefinition = "jsonb")
    private JsonNode additionalInfo;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_by", nullable = false, length = 30)
    private String modifiedBy;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdOn = now;
        modifiedOn = now;
        if (status == null) {
            status = BatchStatus.VALIDATION_IN_PROGRESS;
        }
        if (totalRecords == null) {
            totalRecords = 0;
        }
        if (validRecords == null) {
            validRecords = 0;
        }
        if (failedRecords == null) {
            failedRecords = 0;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
