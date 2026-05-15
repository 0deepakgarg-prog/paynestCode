package com.paynest.payments.qr.entity;

import com.paynest.config.tenant.TenantTime;
import com.paynest.payments.qr.enums.QrIntentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "qr_payment_intent")
@Data
public class QrPaymentIntent {

    @Id
    @Column(name = "qr_intent_id", nullable = false, length = 40)
    private String qrIntentId;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(name = "creditor_identifier_type", nullable = false, length = 30)
    private String creditorIdentifierType;

    @Column(name = "creditor_identifier_value", nullable = false, length = 30)
    private String creditorIdentifierValue;

    @Column(name = "creditor_account_type", nullable = false, length = 30)
    private String creditorAccountType;

    @Column(name = "creditor_wallet_type", nullable = false, length = 50)
    private String creditorWalletType;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "amount")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QrIntentStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "transaction_id", length = 30)
    private String transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = QrIntentStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TenantTime.now();
    }
}
