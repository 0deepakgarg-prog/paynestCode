package com.paynest.users.entity;

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
@Table(name = "account_biller_info")
@Data
public class AccountBillerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "biller_info_id")
    private Long billerInfoId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "biller_category", nullable = false, length = 50)
    private String billerCategory;

    @Column(name = "biller_code", nullable = false, length = 100)
    private String billerCode;

    @Column(name = "biller_sub_category", length = 100)
    private String billerSubCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "biller_config", columnDefinition = "jsonb")
    private JsonNode billerConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "biller_settings", columnDefinition = "jsonb")
    private JsonNode billerSettings;

    @Column(name = "created_on", updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "modified_on")
    private LocalDateTime modifiedOn;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "field1", length = 250)
    private String field1;

    @Column(name = "field2", length = 250)
    private String field2;

    @Column(name = "field3", length = 250)
    private String field3;

    @Column(name = "field4", length = 250)
    private String field4;

    @Column(name = "field5", length = 250)
    private String field5;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        createdOn = now;
        modifiedOn = now;
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedOn = TenantTime.now();
    }
}
