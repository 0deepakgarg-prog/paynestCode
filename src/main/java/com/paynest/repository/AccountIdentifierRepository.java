package com.paynest.repository;

import com.paynest.entity.AccountIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountIdentifierRepository extends JpaRepository<AccountIdentifier, Long> {

    // Custom query methods can be added here if needed
}
