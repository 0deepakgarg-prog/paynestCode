package com.paynest.documents.repository;

import com.paynest.documents.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    Optional<DocumentType> findByTypeCodeAndIsActiveTrue(String typeCode);

    List<DocumentType> findByIsActiveTrueOrderByTypeNameAsc();

    List<DocumentType> findByCategoryIdAndIsActiveTrueOrderByTypeNameAsc(Long categoryId);
}
