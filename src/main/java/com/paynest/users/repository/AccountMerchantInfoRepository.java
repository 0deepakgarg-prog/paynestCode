package com.paynest.users.repository;

import com.paynest.users.entity.AccountMerchantInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMerchantInfoRepository extends JpaRepository<AccountMerchantInfo, Long> {

    boolean existsByMerchantCodeIgnoreCase(String merchantCode);
}
