package com.paynest.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_challenge")
@Data
public class AuthChallenge {

    @Id
    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "challenge_value", nullable = false)
    private String challengeValue;

    @Column(name = "challenge_type", nullable = false, length = 30)
    private String challengeType;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used")
    private Boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @PrePersist
    protected void onCreate() {
        if (this.challengeId == null) {
            this.challengeId = UUID.randomUUID();
        }
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
        if (this.used == null) {
            this.used = false;
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (Boolean.TRUE.equals(this.used) && this.usedAt == null) {
            this.usedAt = LocalDateTime.now();
        }
    }
}

