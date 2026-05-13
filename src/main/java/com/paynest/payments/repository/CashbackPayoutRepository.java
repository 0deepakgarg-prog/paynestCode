package com.paynest.payments.repository;

import com.paynest.payments.entity.CashbackPayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashbackPayoutRepository extends JpaRepository<CashbackPayout, Long> {

    List<CashbackPayout> findTop100ByStatusAndPayAtLessThanEqualOrderByPayAtAsc(
            String status,
            LocalDateTime payAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CashbackPayout> findFirstByCashbackPayoutIdAndStatus(Long cashbackPayoutId, String status);
}
