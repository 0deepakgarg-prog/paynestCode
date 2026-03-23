package com.paynest.users.repository;

import com.paynest.users.entity.AccountAuth;
import com.paynest.users.entity.AccountIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountAuthRepository extends JpaRepository<AccountAuth, Long> {
    List<AccountAuth> findByIdAndStatus(Long Id, String status);

    // Custom query methods can be added here if needed
}

