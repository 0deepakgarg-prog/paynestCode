package com.paynest.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_type")
@Getter
@Setter
public class DocumentType {

    @Id
    @Column(name = "document_type_id")
    private Long documentTypeId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "type_code", nullable = false, length = 75)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 150)
    private String typeName;

    @Column(name = "multiple_allowed", nullable = false)
    private Boolean multipleAllowed;

    @Column(name = "verification_required", nullable = false)
    private Boolean verificationRequired;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
