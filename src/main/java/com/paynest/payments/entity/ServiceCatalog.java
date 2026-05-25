package com.paynest.payments.entity;


import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_catalog")
@Data
public class ServiceCatalog {

    @Id
    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "service_category", length = 50)
    private String serviceCategory;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "is_financial", nullable = false)
    private Boolean isFinancial = true;

    @Column(name = "send_to_integrator", nullable = false)
    private Boolean sendToIntegrator = false;

    @Column(name = "requires_confirmation", nullable = false)
    private Boolean requiresConfirmation = false;

    @Column(name = "integrator_call_mode", nullable = false, length = 20)
    private String integratorCallMode = "SYNC";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = TenantTime.now();
        if (isFinancial == null) {
            isFinancial = true;
        }
        if (sendToIntegrator == null) {
            sendToIntegrator = false;
        }
        if (requiresConfirmation == null) {
            requiresConfirmation = false;
        }
        if (integratorCallMode == null || integratorCallMode.isBlank()) {
            integratorCallMode = "SYNC";
        }
        if (isActive == null) {
            isActive = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TenantTime.now();
    }
}
