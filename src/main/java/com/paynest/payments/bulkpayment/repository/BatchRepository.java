package com.paynest.payments.bulkpayment.repository;

import com.paynest.payments.bulkpayment.entity.Batch;
import com.paynest.payments.bulkpayment.enums.BatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, String> {

    boolean existsByBatchReference(String batchReference);

    Optional<Batch> findByBatchReference(String batchReference);

    List<Batch> findByStatus(BatchStatus status);

    boolean existsByStatusIn(List<BatchStatus> statuses);

    Optional<Batch> findFirstByStatusOrderByCreatedOnAsc(BatchStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Batch> findFirstByBatchId(String batchId);
}
