package com.paynest.repository;

import com.paynest.entity.AccountAuth;
import com.paynest.entity.AccountIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountAuthRepository extends JpaRepository<AccountAuth, Long> {

    // Custom query methods can be added here if needed
}
