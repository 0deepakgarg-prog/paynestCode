package com.paynest.limits.repository;

import com.paynest.limits.entity.TransactionLimitProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLimitProfileRepository extends JpaRepository<TransactionLimitProfile, Long> {

    List<TransactionLimitProfile> findAllByOrderByCreatedOnDesc();

    List<TransactionLimitProfile> findByTagIdInAndLimitTypeAndWalletTypeAndCurrencyAndStatusOrderByCreatedOnDesc(
            List<Long> tagIds,
            String limitType,
            String walletType,
            String currency,
            String status
    );
}
