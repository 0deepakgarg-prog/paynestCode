package com.paynest.limits.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_limit_usage")
@Data
public class TransactionLimitUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usage_id")
    private Long usageId;

    @Column(name = "subject_key", nullable = false, length = 50)
    private String subjectKey;

    @Column(name = "subject_value", nullable = false, length = 200)
    private String subjectValue;

    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "limit_id", nullable = false)
    private Long limitId;

    @Column(name = "limit_details_id", nullable = false)
    private Long limitDetailsId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "request_gateway", nullable = false, length = 50)
    private String requestGateway;

    @Column(name = "payer_count", nullable = false)
    private Integer payerCount;

    @Column(name = "payer_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal payerAmount;

    @Column(name = "payee_count", nullable = false)
    private Integer payeeCount;

    @Column(name = "payee_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal payeeAmount;

    @Column(name = "last_transaction_id", length = 30)
    private String lastTransactionId;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @PrePersist
    protected void onCreate() {
        if (payerCount == null) {
            payerCount = 0;
        }
        if (payerAmount == null) {
            payerAmount = BigDecimal.ZERO;
        }
        if (payeeCount == null) {
            payeeCount = 0;
        }
        if (payeeAmount == null) {
            payeeAmount = BigDecimal.ZERO;
        }
    }
}
