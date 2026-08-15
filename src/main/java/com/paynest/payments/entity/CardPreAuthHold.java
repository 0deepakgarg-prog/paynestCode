package com.paynest.payments.entity;

import com.paynest.config.tenant.TenantTime;
import com.paynest.payments.enums.CardPreAuthHoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_pre_auth_hold")
@Data
public class CardPreAuthHold {

    @Id
    @Column(name = "hold_id", nullable = false, length = 50)
    private String holdId;

    @Column(name = "cms_transaction_id", nullable = false, unique = true, length = 100)
    private String cmsTransactionId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "account_id", nullable = false, length = 30)
    private String accountId;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "wallet_type", nullable = false, length = 50)
    private String walletType;

    @Column(name = "original_amount", nullable = false)
    private BigDecimal originalAmount;

    @Column(name = "hold_amount", nullable = false)
    private BigDecimal holdAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardPreAuthHoldStatus status;

    @Column(name = "cms_reference", length = 100)
    private String cmsReference;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    @Column(name = "comments", length = 300)
    private String comments;

    @Column(name = "additional_info", length = 4000)
    private String additionalInfo;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdOn = now;
        modifiedOn = now;
        if (status == null) {
            status = CardPreAuthHoldStatus.HELD;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
