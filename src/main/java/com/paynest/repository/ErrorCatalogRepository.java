package com.paynest.repository;

import com.paynest.entity.ErrorCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ErrorCatalogRepository extends JpaRepository<ErrorCatalog, Long> {

    Optional<ErrorCatalog> findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue(
            String errorCode,
            String languageCode
    );
}
