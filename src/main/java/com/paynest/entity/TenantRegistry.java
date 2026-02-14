package com.paynest.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_registry", schema = "public")
@Data
public class TenantRegistry {

    @Id
    @Column(name = "tenant_id", length = 50)
    private String tenantId;

    @Column(name = "tenant_name", length = 100)
    private String tenantName;

    @Column(name = "schema_name", length = 100)
    private String schemaName;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
