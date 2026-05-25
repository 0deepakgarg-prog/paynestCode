package com.paynest.documents.repository;

import com.paynest.documents.entity.DocumentTypeEntity;
import com.paynest.documents.entity.DocumentTypeEntityId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentTypeEntityRepository extends JpaRepository<DocumentTypeEntity, DocumentTypeEntityId> {

    boolean existsByIdDocumentTypeIdAndIdEntityType(Long documentTypeId, String entityType);

    List<DocumentTypeEntity> findByIdEntityType(String entityType);
}
