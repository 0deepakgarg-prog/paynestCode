package com.paynest.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "recent_recipients")
@IdClass(RecentRecipientId.class)
@Data
public class RecentRecipient {

    @Id
    @Column(name = "account_id", nullable = false, length = 30)
    private String accountId;

    @Id
    @Column(name = "recipient_account_id", nullable = false, length = 30)
    private String recipientAccountId;

    @Id
    @Column(name = "service_code", nullable = false, length = 15)
    private String serviceCode;

    @Id
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Id
    @Column(name = "wallet_type", nullable = false, length = 50)
    private String walletType;

    @Column(name = "recipient_account_type", length = 50)
    private String recipientAccountType;

    @Column(name = "recipient_identifier_type", length = 30)
    private String recipientIdentifierType;

    @Column(name = "recipient_identifier_value", length = 100)
    private String recipientIdentifierValue;

    @Column(name = "recipient_display_name", length = 200)
    private String recipientDisplayName;

    @Column(name = "last_transaction_id", length = 30)
    private String lastTransactionId;

    @Column(name = "last_paid_at")
    private LocalDateTime lastPaidAt;

    @Column(name = "payment_count")
    private Long paymentCount;

    @Column(name = "field1", length = 250)
    private String field1;

    @Column(name = "field2", length = 250)
    private String field2;

    @Column(name = "field3", length = 250)
    private String field3;

    @Column(name = "field4", length = 250)
    private String field4;

    @Column(name = "field5", length = 250)
    private String field5;

    @Column(name = "created_on")
    private LocalDateTime createdOn;

    @Column(name = "modified_on")
    private LocalDateTime modifiedOn;
}
