package com.paynest.users.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_restriction")
@Data
public class WalletRestriction {

    @Id
    @Column(name = "wallet_id")
    private Long walletId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restrictions", nullable = false, columnDefinition = "jsonb")
    private JsonNode restrictions;

    @Column(name = "version")
    private Long version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = 0L;
        }
        updatedAt = TenantTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TenantTime.now();
    }
}
