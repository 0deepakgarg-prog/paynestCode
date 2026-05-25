package com.paynest.documents.entity;

import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_reference")
@Getter
@Setter
public class DocumentReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_reference_id")
    private Long documentReferenceId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "reference_role", nullable = false, length = 30)
    private String referenceRole;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdAt = now;
        updatedAt = now;
        if (referenceRole == null) {
            referenceRole = "OWNER";
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
        if (isPrimary == null) {
            isPrimary = Boolean.FALSE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TenantTime.now();
    }
}
