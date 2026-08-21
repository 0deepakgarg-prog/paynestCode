package com.paynest.limits.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_limit_profile")
@Data
public class TransactionLimitProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "limit_id")
    private Long limitId;

    @Column(name = "limit_name", nullable = false, length = 150)
    private String limitName;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "limit_type", nullable = false, length = 20)
    private String limitType;

    @Column(name = "subject_key", nullable = false, length = 50)
    private String subjectKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private JsonNode details;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "wallet_type", nullable = false, length = 50)
    private String walletType;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "min_residual_balance", precision = 19, scale = 0)
    private BigDecimal minResidualBalance;

    @Column(name = "max_balance", precision = 19, scale = 0)
    private BigDecimal maxBalance;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

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
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
