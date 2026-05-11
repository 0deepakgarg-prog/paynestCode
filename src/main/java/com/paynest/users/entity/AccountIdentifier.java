package com.paynest.users.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_identifiers")
@Data
public class AccountIdentifier {

    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Column(nullable = false)
    private String accountId;

    @JsonIgnore
    @Column(nullable = false)
    private Long authId;

    @Column(nullable = false, length = 50)
    private String identifierType;

    @Column(nullable = false, length = 255)
    private String identifierValue;

    @Column(nullable = false, length = 20)
    private String status;

    @JsonIgnore
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = TenantTime.now();
    }
}
