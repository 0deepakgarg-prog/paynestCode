package com.paynest.payments.entity;

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
@Table(name = "cashback_payout")
@Data
public class CashbackPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cashback_payout_id")
    private Long cashbackPayoutId;

    @Column(name = "original_transaction_id", nullable = false, length = 30)
    private String originalTransactionId;

    @Column(name = "payout_transaction_id", length = 30)
    private String payoutTransactionId;

    @Column(name = "service_code", nullable = false, length = 15)
    private String serviceCode;

    @Column(name = "beneficiary_account_id", nullable = false, length = 30)
    private String beneficiaryAccountId;

    @Column(name = "beneficiary_party", length = 20)
    private String beneficiaryParty;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "payment_schedule", nullable = false, length = 30)
    private String paymentSchedule;

    @Column(name = "pay_at", nullable = false)
    private LocalDateTime payAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "pricing_rule_details", length = 4000)
    private String pricingRuleDetails;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdOn = now;
        modifiedOn = now;
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
