package com.paynest.documents.repository;

import com.paynest.documents.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    Optional<StoredDocument> findByDocumentIdAndStatus(UUID documentId, String status);
}
