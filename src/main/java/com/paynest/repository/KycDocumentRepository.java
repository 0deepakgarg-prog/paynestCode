package com.paynest.repository;

import com.paynest.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByAccountIdAndIsActiveTrue(String accountId);

    Optional<KycDocument> findByAccountIdAndDocumentType(String accountId, String documentType);

    List<KycDocument> findByVerificationStatus(String verificationStatus);

    boolean existsByAccountIdAndDocumentType(String accountId, String documentType);
}