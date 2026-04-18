package com.paynest.users.entity;


import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "kyc_document"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "document_url", nullable = false)
    private String documentUrl;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "PENDING";

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /* ---------------- Lifecycle Hooks ---------------- */

    @PrePersist
    public void prePersist() {
        this.createdAt = TenantTime.now();
        this.updatedAt = TenantTime.now();
        if (this.verificationStatus == null) {
            this.verificationStatus = "PENDING";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = TenantTime.now();
    }
}
