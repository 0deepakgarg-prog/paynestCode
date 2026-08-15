package com.paynest.payments.bulkpayment.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.payments.bulkpayment.enums.BatchDetailStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "batch_details")
@Data
public class BatchDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_detail_id")
    private Long batchDetailId;

    @Column(name = "batch_id", nullable = false, length = 30)
    private String batchId;

    @Column(name = "item_reference", length = 100)
    private String itemReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BatchDetailStatus status;

    @Column(name = "transaction_id", length = 30)
    private String transactionId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "creditor_wallet_type", length = 50)
    private String creditorWalletType;

    @Column(name = "creditor_currency", length = 10)
    private String creditorCurrency;

    @Column(name = "creditor_identifier_type", length = 30)
    private String creditorIdentifierType;

    @Column(name = "creditor_identifier_value", length = 50)
    private String creditorIdentifierValue;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "comments", length = 300)
    private String comments;

    @Column(name = "validation_error_code", length = 100)
    private String validationErrorCode;

    @Column(name = "validation_error_message", length = 500)
    private String validationErrorMessage;

    @Column(name = "processing_error_code", length = 100)
    private String processingErrorCode;

    @Column(name = "processing_error_message", length = 500)
    private String processingErrorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_info", columnDefinition = "jsonb")
    private JsonNode additionalInfo;
}
