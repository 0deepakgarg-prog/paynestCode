package com.paynest.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
public class Transactions {
    @Id
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "transfer_on")
    private LocalDateTime transferOn;

    @Column(name = "transaction_value")
    private BigDecimal transactionValue;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "transfer_status")
    private String transferStatus;

    @Column(name = "request_gateway")
    private String requestGateway;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "reconciliation_done")
    private String reconciliationDone;

    @Column(name = "reconciliation_date")
    private LocalDateTime reconciliationDate;

    @Column(name = "reconciliation_by")
    private String reconciliationBy;

    @Column(name = "language")
    private String language;

    @Column(name = "country")
    private String country;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_by", nullable = false)
    private String modifiedBy;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @Column(name = "comments")
    private String comments;

    @Column(name = "debtor_account_id")
    private String debtorAccountId;

    @Column(name = "creditor_account_id")
    private String creditorAccountId;

    @Column(name = "fees_details")
    private String feesDetails;

    @Column(name = "additional_info")
    private String additionalInfo;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "field1")
    private String field1;

    @Column(name = "field2")
    private String field2;

    @Column(name = "field3")
    private String field3;

    @Column(name = "field4")
    private String field4;

    @Column(name = "field5")
    private String field5;

    @Column(name = "field6")
    private String field6;

    @Column(name = "field7")
    private String field7;

    @Column(name = "field8")
    private String field8;

    @Column(name = "field9")
    private String field9;

    @Column(name = "field10")
    private String field10;

    @Column(name = "core_service_code")
    private String coreServiceCode;

    @Column(name = "debtor_identifier_type")
    private String debtorIdentifierType;

    @Column(name = "debtor_identifier_value")
    private String debtorIdentifierValue;

    @Column(name = "creditor_identifier_type")
    private String creditorIdentifierType;

    @Column(name = "creditor_identifier_value")
    private String creditorIdentifierValue;

    @Column(name = "previous_status")
    private String previousStatus;

}

