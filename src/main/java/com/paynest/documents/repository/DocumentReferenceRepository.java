package com.paynest.documents.repository;

import com.paynest.documents.entity.DocumentReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentReferenceRepository extends JpaRepository<DocumentReference, Long> {

    List<DocumentReference> findByDocumentIdAndIsActiveTrue(UUID documentId);

    List<DocumentReference> findByEntityTypeAndEntityIdAndIsActiveTrueOrderByCreatedAtDesc(
            String entityType,
            String entityId
    );

    List<DocumentReference> findByEntityIdAndIsActiveTrueOrderByCreatedAtDesc(String entityId);
}
