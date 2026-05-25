package com.paynest.users.repository;

import com.paynest.users.entity.AccountBillerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountBillerInfoRepository extends JpaRepository<AccountBillerInfo, Long> {

    boolean existsByBillerCodeIgnoreCase(String billerCode);
}
