package com.paynest.users.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "wallet_restriction_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_restriction_history_wallet_version",
                columnNames = {"wallet_id", "version"}
        )
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WalletRestrictionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "version", nullable = false)
    private Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restrictions", nullable = false, columnDefinition = "jsonb")
    private JsonNode restrictions;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WalletRestrictionHistory create(
            Long walletId,
            Long version,
            JsonNode restrictions,
            String actionType,
            String changedBy) {
        return new WalletRestrictionHistory(
                null,
                walletId,
                version,
                restrictions,
                actionType,
                changedBy,
                null
        );
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = TenantTime.now();
        }
    }
}
