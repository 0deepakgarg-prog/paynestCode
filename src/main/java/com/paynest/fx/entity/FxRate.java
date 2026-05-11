package com.paynest.fx.entity;

import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "fx_rates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_fx_active", columnNames = {"target_currency", "version_no"})
        }
)
@Data
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rate_id")
    private Long rateId;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "usd_rate", nullable = false, precision = 20, scale = 10)
    private BigDecimal usdRate;

    @Column(name = "rate_type", nullable = false, length = 20)
    private String rateType;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "version_no", nullable = false, updatable = false)
    private Long versionNo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "field1", length = 100)
    private String field1;

    @Column(name = "field2", length = 100)
    private String field2;

    @Column(name = "field3", length = 100)
    private String field3;

    @Column(name = "field4", length = 100)
    private String field4;

    @Column(name = "field5", length = 100)
    private String field5;

    @PrePersist
    protected void onCreate() {
        if (rateType == null || rateType.isBlank()) {
            rateType = "MID";
        }
        if (isActive == null) {
            isActive = true;
        }
        if (createdAt == null) {
            createdAt = TenantTime.now();
        }
    }
}
