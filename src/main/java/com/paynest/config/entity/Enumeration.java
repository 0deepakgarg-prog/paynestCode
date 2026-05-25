package com.paynest.config.entity;


import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "enumerations")
@Data
public class Enumeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enum_type", length = 50, nullable = false)
    private String enumType;

    @Column(name = "enum_code", length = 50, nullable = false)
    private String enumCode;

    @Column(name = "enum_value", length = 100, nullable = false)
    private String enumValue;

    @Column(name = "parent_enum_id")
    private Long parentEnumId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_system")
    private Boolean isSystem = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = TenantTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
        this.createdAt = TenantTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TenantTime.now();
    }
}
