package com.paynest.repository;

import com.paynest.entity.SupportedLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportedLanguageRepository extends JpaRepository<SupportedLanguage, Long> {
    Optional<SupportedLanguage> findByLanguageCodeIgnoreCaseAndIsActiveTrue(String languageCode);

    Optional<SupportedLanguage> findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc();
}
