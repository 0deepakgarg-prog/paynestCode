package com.paynest.limits.repository;

import com.paynest.limits.entity.TransactionLimitProfilePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLimitProfilePeriodRepository extends JpaRepository<TransactionLimitProfilePeriod, Long> {

    List<TransactionLimitProfilePeriod> findByLimitDetailsIdOrderByLimitPeriodIdAsc(Long limitDetailsId);

    List<TransactionLimitProfilePeriod> findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
            Long limitDetailsId,
            String status
    );
}
