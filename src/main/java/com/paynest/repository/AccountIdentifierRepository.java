package com.paynest.repository;

import com.paynest.entity.AccountIdentifier;
import com.paynest.enums.IdentifierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountIdentifierRepository extends JpaRepository<AccountIdentifier, Long> {

    // Custom query methods can be added here if needed

    List<AccountIdentifier> findByAccountIdAndStatus(
            String accountId,
            String status
    );


    Optional<AccountIdentifier> findByIdentifierTypeAndIdentifierValueAndStatus(
            String identifierType,
            String identifierValue,
            String status
    );
}
