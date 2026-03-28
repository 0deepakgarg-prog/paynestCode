package com.paynest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "error_catalog",
        uniqueConstraints = @UniqueConstraint(columnNames = {"error_code", "language_code"})
)
@Data
public class ErrorCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "message_template", nullable = false, length = 2000)
    private String messageTemplate;

    @Column(name = "http_status", nullable = false)
    private Integer httpStatus = 400;

    @Column(name = "category", length = 30)
    private String category;

    @Column(name = "module", length = 30)
    private String module;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
