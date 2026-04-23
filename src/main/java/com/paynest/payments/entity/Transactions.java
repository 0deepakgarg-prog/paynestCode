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
    @Column(name = "transaction_id", nullable = false, length = 30)
    private String transactionId;

    @Column(name = "transfer_on")
    private LocalDateTime transferOn;

    @Column(name = "transaction_value")
    private BigDecimal transactionValue;

    @Column(name = "error_code", length = 200)
    private String errorCode;

    @Column(name = "transfer_status", length = 3)
    private String transferStatus;

    @Column(name = "request_gateway", length = 10)
    private String requestGateway;

    @Column(name = "service_code", length = 15)
    private String serviceCode;

    @Column(name = "trace_id", length = 50)
    private String traceId;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "reconciliation_done", length = 3)
    private String reconciliationDone;

    @Column(name = "reconciliation_date")
    private LocalDateTime reconciliationDate;

    @Column(name = "reconciliation_by", length = 30)
    private String reconciliationBy;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "country", length = 20)
    private String country;

    @Column(name = "created_by", nullable = false, length = 30)
    private String createdBy;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_by", nullable = false, length = 30)
    private String modifiedBy;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @Column(name = "comments", length = 300)
    private String comments;

    @Column(name = "debitor_account_id", length = 30)
    private String debitorAccountId;

    @Column(name = "creditor_account_id", length = 30)
    private String creditorAccountId;

    @Column(name = "fees_details", length = 4000)
    private String feesDetails;

    @Column(name = "additional_info", length = 4000)
    private String additionalInfo;

    @Column(name = "metadata", length = 4000)
    private String metadata;

    @Column(name = "attr_1_name", length = 255)
    private String attr1Name;

    @Column(name = "attr_1_value", length = 255)
    private String attr1Value;

    @Column(name = "attr_2_name", length = 255)
    private String attr2Name;

    @Column(name = "attr_2_value", length = 255)
    private String attr2Value;

    @Column(name = "attr_3_name", length = 255)
    private String attr3Name;

    @Column(name = "attr_3_value", length = 255)
    private String attr3Value;

    @Column(name = "attr_4_name", length = 255)
    private String attr4Name;

    @Column(name = "attr_4_value", length = 255)
    private String attr4Value;

    @Column(name = "attr_5_name", length = 255)
    private String attr5Name;

    @Column(name = "attr_5_value", length = 255)
    private String attr5Value;

    @Column(name = "attr_6_name", length = 255)
    private String attr6Name;

    @Column(name = "attr_6_value", length = 255)
    private String attr6Value;

    @Column(name = "field1", length = 100)
    private String field1;

    @Column(name = "field2", length = 100)
    private String field2;

    @Column(name = "field3", length = 100)
    private String field3;

    @Column(name = "field4", length = 100)
    private String field4;

    @Column(name = "field5", length = 100)
    private String field5;

    @Column(name = "field6", length = 100)
    private String field6;

    @Column(name = "field7", length = 100)
    private String field7;

    @Column(name = "field8", length = 100)
    private String field8;

    @Column(name = "field9", length = 100)
    private String field9;

    @Column(name = "field10", length = 100)
    private String field10;

    @Column(name = "core_service_code", length = 100)
    private String coreServiceCode;

    @Column(name = "debitor_identifier_type", length = 30)
    private String debitorIdentifierType;

    @Column(name = "debitor_identifier_value", length = 30)
    private String debitorIdentifierValue;

    @Column(name = "creditor_identifier_type", length = 30)
    private String creditorIdentifierType;

    @Column(name = "creditor_identifier_value", length = 30)
    private String creditorIdentifierValue;

    @Column(name = "previous_status", length = 5)
    private String previousStatus;

}

