package com.paynest.pricing.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_rules")
@Data
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pricing_name", nullable = false, length = 100)
    private String pricingName;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "rule_type", nullable = false, length = 30)
    private String ruleType;

    @Column(name = "pricing_type", length = 20)
    private String pricingType;

    @Column(name = "payer", nullable = false, length = 20)
    private String payer;

    @Column(name = "pay_by", length = 20)
    private String payBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payer_split", columnDefinition = "jsonb")
    private JsonNode payerSplit;

    @Column(name = "sender_tag_key", nullable = false, length = 255)
    private String senderTagKey;

    @Column(name = "receiver_tag_key", nullable = false, length = 255)
    private String receiverTagKey;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_config", nullable = false, columnDefinition = "jsonb")
    private JsonNode pricingConfig;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (validFrom == null) {
            validFrom = now;
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
