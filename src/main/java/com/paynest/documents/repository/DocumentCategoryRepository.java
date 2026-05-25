package com.paynest.documents.repository;

import com.paynest.documents.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    List<DocumentCategory> findByIsActiveTrueOrderByCategoryNameAsc();

    Optional<DocumentCategory> findByCategoryCodeAndIsActiveTrue(String categoryCode);
}
