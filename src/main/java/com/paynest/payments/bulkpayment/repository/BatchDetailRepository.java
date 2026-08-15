package com.paynest.payments.bulkpayment.repository;

import com.paynest.payments.bulkpayment.entity.BatchDetail;
import com.paynest.payments.bulkpayment.enums.BatchDetailStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchDetailRepository extends JpaRepository<BatchDetail, Long> {

    List<BatchDetail> findByBatchId(String batchId);

    List<BatchDetail> findByBatchIdAndStatus(String batchId, BatchDetailStatus status);

    long countByBatchIdAndStatus(String batchId, BatchDetailStatus status);

    long countByBatchIdAndStatusIn(String batchId, List<BatchDetailStatus> statuses);

    boolean existsByBatchIdAndItemReference(String batchId, String itemReference);

    boolean existsByBatchIdAndStatus(String batchId, BatchDetailStatus status);

    @Query("""
            SELECT COALESCE(SUM(d.amount), 0)
            FROM BatchDetail d
            WHERE d.batchId = :batchId
              AND d.status = :status
            """)
    BigDecimal sumAmountByBatchIdAndStatus(String batchId, BatchDetailStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BatchDetail> findFirstByBatchDetailId(Long batchDetailId);

    @Query(value = """
            SELECT *
            FROM batch_details
            WHERE batch_id = :batchId
              AND status = :status
            ORDER BY batch_detail_id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<BatchDetail> findNextDetailsForUpdate(String batchId, String status, int limit);
}
