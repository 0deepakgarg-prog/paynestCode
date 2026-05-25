package com.paynest.users.entity;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "account_merchant_mcc")
@Data
public class AccountMerchantMcc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_mcc_id")
    private Long merchantMccId;

    @Column(name = "merchant_info_id", nullable = false)
    private Long merchantInfoId;

    @Column(name = "mcc_code", nullable = false, length = 4)
    private String mccCode;

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
