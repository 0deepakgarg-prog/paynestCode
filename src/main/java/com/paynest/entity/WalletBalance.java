
package com.paynest.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_balance")
@Data
public class WalletBalance {

    @Id
    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "frozen_balance", nullable = false)
    private BigDecimal frozenBalance;

    @Column(name = "fic_balance", nullable = false)
    private BigDecimal ficBalance;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /* ==========================
       Lifecycle Callbacks
       ========================== */

    @PrePersist
    protected void onCreate() {
        if (availableBalance == null)
            availableBalance = BigDecimal.ZERO;

        if (frozenBalance == null)
            frozenBalance = BigDecimal.ZERO;

        if (ficBalance == null)
            ficBalance = BigDecimal.ZERO;

        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}

