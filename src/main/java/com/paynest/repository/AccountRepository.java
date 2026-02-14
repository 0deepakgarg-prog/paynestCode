package com.paynest.repository;

import com.paynest.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    // Read by mobile number
    Optional<Account> findByMobileNumber(String mobileNumber);

    // Read active accounts
    Optional<Account> findByAccountIdAndStatus(String accountId, String status);

}

