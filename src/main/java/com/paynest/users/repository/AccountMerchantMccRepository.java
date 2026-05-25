package com.paynest.users.repository;

import com.paynest.users.entity.AccountMerchantMcc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMerchantMccRepository extends JpaRepository<AccountMerchantMcc, Long> {
}
