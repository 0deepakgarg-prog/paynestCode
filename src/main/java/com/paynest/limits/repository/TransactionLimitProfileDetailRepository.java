package com.paynest.limits.repository;

import com.paynest.limits.entity.TransactionLimitProfileDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLimitProfileDetailRepository extends JpaRepository<TransactionLimitProfileDetail, Long> {

    List<TransactionLimitProfileDetail> findByLimitIdOrderByLimitDetailsIdAsc(Long limitId);

    List<TransactionLimitProfileDetail> findByLimitIdAndStatusOrderByLimitDetailsIdAsc(Long limitId, String status);

    List<TransactionLimitProfileDetail> findByLimitIdAndPartyTypeAndStatusOrderByLimitDetailsIdAsc(
            Long limitId,
            String partyType,
            String status
    );
}
