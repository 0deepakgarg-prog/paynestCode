package com.paynest.payments.entity;

import com.paynest.config.tenant.TenantTime;
import com.paynest.payments.enums.PasscodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "passcode")
@Data
public class Passcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "passcode_id")
    private Long passcodeId;

    @Column(name = "transaction_id", nullable = false, length = 30)
    private String transactionId;

    @Column(name = "cashout_transaction_id", length = 30)
    private String cashoutTransactionId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "unregistered_msisdn", nullable = false, length = 30)
    private String unregisteredMsisdn;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "kyc_document_id", length = 100)
    private String kycDocumentId;

    @Column(name = "sender_msisdn", length = 30)
    private String senderMsisdn;

    @Column(name = "sender_account_id", nullable = false, length = 30)
    private String senderAccountId;

    @Column(name = "passcode", nullable = false, unique = true, length = 10)
    private String passcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PasscodeStatus status;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @Column(name = "redeemed_on")
    private LocalDateTime redeemedOn;

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

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdOn = now;
        modifiedOn = now;
        if (status == null) {
            status = PasscodeStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
