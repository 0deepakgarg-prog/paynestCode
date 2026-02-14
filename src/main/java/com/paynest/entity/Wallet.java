package com.paynest.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet")
@Data
public class Wallet {

    @Id
    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "wallet_type", nullable = false)
    private String walletType;

    @Column(name = "status")
    private String status;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "is_locked")
    private Boolean isLocked;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "remarks")
    private String remarks;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = "ACTIVE";
        }
        if (isLocked == null) {
            isLocked = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}

