package com.paynest.users.repository;

import com.paynest.users.entity.WalletRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRestrictionRepository extends JpaRepository<WalletRestriction, Long> {
}
