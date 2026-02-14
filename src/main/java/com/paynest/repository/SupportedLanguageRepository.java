package com.paynest.repository;

import com.paynest.entity.SupportedLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportedLanguageRepository extends JpaRepository<SupportedLanguage, Long> {
    // Custom query methods can be added here if needed
}
