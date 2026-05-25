package com.paynest.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTypeEntityId implements Serializable {

    @Column(name = "document_type_id")
    private Long documentTypeId;

    @Column(name = "entity_type", length = 30)
    private String entityType;
}
