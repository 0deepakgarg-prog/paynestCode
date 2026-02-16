package com.paynest.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp")
@Data
public class Otp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_id")
    private Long otpId;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "otp_value")
    private Integer otpValue;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "max_attempt")
    private Integer maxAttempt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ---------- Lifecycle Hooks ----------

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.status = this.status == null ? "CREATED" : this.status;
        this.attemptCount = this.attemptCount == null ? 0 : this.attemptCount;
        this.maxAttempt = this.maxAttempt == null ? 3 : this.maxAttempt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}