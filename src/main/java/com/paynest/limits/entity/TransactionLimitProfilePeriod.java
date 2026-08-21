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
@Table(name = "transaction_limit_profile_period")
@Data
public class TransactionLimitProfilePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "limit_period_id")
    private Long limitPeriodId;

    @Column(name = "limit_details_id", nullable = false)
    private Long limitDetailsId;

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;

    @Column(name = "max_count")
    private Integer maxCount;

    @Column(name = "max_amount", precision = 19, scale = 0)
    private BigDecimal maxAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

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
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
