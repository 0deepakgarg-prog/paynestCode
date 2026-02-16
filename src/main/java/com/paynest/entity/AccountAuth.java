package com.paynest.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_auth")
@Data
public class AccountAuth {

    @Id
    private Long id;

    @Column(name = "auth_hash", length = 255)
    private String authHash;

    @Column(name = "auth_value", length = 255)
    private String authValue;

    @Column(name = "auth_type", length = 20, nullable = false)
    private String authType = "PIN";

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "is_first_time_login")
    private Boolean isFirstTimeLogin = false;

    @Column(name = "last_failed_at")
    private LocalDateTime lastFailedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
