package com.paynest.payments.entity;

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
@Table(name = "third_party_response")
@Data
public class ThirdPartyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 50)
    private String transactionId;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "integrator_name", length = 100)
    private String integratorName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", nullable = false, columnDefinition = "jsonb")
    private JsonNode requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private JsonNode responseBody;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "modified_on", nullable = false)
    private LocalDateTime modifiedOn;

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
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
