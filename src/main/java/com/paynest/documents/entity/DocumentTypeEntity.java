package com.paynest.documents.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_type_entity")
@Getter
@Setter
public class DocumentTypeEntity {

    @EmbeddedId
    private DocumentTypeEntityId id;
}
