package com.paynest.config.repository;

import com.paynest.config.entity.SupportedLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportedLanguageRepository extends JpaRepository<SupportedLanguage, Long> {
    // Custom query methods can be added here if needed
}

