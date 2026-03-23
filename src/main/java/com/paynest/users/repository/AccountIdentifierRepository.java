package com.paynest.users.repository;

import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.enums.IdentifierType;
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

    List<AccountIdentifier> findByAccountId(String accountId);


    Optional<AccountIdentifier> findByIdentifierTypeAndIdentifierValueAndStatus(
            String identifierType,
            String identifierValue,
            String status
    );
}

