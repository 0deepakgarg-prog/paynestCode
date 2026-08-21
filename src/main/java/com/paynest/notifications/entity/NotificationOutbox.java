package com.paynest.notifications.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.config.tenant.TenantTime;
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
@Table(name = "notification_outbox")
@Data
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "transaction_id", length = 30)
    private String transactionId;

    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "party_role", length = 20)
    private String partyRole;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 2000)
    private String recipient;

    @Column(name = "recipient_masked", length = 200)
    private String recipientMasked;

    @Column(name = "template_code", length = 200)
    private String templateCode;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "notification_text", nullable = false, columnDefinition = "text")
    private String notificationText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "service_code", length = 15)
    private String serviceCode;

    @Column(name = "transfer_status", length = 10)
    private String transferStatus;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

    @Column(name = "sent_on")
    private LocalDateTime sentOn;

    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        if (createdOn == null) {
            createdOn = now;
        }
        if (modifiedOn == null) {
            modifiedOn = now;
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
