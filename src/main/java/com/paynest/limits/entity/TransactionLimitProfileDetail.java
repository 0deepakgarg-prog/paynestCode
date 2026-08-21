package com.paynest.limits.entity;

import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_limit_profile_details")
@Data
public class TransactionLimitProfileDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "limit_details_id")
    private Long limitDetailsId;

    @Column(name = "limit_id", nullable = false)
    private Long limitId;

    @Column(name = "party_type", nullable = false, length = 20)
    private String partyType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "request_gateway", nullable = false, length = 50)
    private String requestGateway;

    @Column(name = "min_txn_amount", precision = 19, scale = 0)
    private BigDecimal minTxnAmount;

    @Column(name = "max_txn_amount", precision = 19, scale = 0)
    private BigDecimal maxTxnAmount;

    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on")
    private LocalDateTime modifiedOn;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        if (createdOn == null) {
            createdOn = now;
        }
        if (modifiedOn == null) {
            modifiedOn = now;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (operationType == null || operationType.isBlank()) {
            operationType = "ALL";
        }
        if (requestGateway == null || requestGateway.isBlank()) {
            requestGateway = "ALL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
