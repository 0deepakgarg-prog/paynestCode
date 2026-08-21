package com.paynest.limits.repository;

import com.paynest.limits.entity.TransactionLimitUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionLimitUsageRepository extends JpaRepository<TransactionLimitUsage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransactionLimitUsage> findBySubjectKeyAndSubjectValueAndLimitIdAndLimitDetailsIdAndPeriodTypeAndOperationTypeAndRequestGateway(
            String subjectKey,
            String subjectValue,
            Long limitId,
            Long limitDetailsId,
            String periodType,
            String operationType,
            String requestGateway
    );

    List<TransactionLimitUsage> findByAccountIdOrderByLastTransactionDateDescUsageIdDesc(String accountId);

    List<TransactionLimitUsage> findByAccountIdAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
            String accountId,
            String periodType
    );

    List<TransactionLimitUsage> findBySubjectKeyAndSubjectValueOrderByLastTransactionDateDescUsageIdDesc(
            String subjectKey,
            String subjectValue
    );

    List<TransactionLimitUsage> findBySubjectKeyAndSubjectValueAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
            String subjectKey,
            String subjectValue,
            String periodType
    );

    @Modifying
    @Query(value = """
            INSERT INTO transaction_limit_usage (
                subject_key,
                subject_value,
                account_id,
                limit_id,
                limit_details_id,
                tag_id,
                period_type,
                operation_type,
                request_gateway,
                payer_count,
                payer_amount,
                payee_count,
                payee_amount
            ) VALUES (
                :subjectKey,
                :subjectValue,
                :accountId,
                :limitId,
                :limitDetailsId,
                :tagId,
                :periodType,
                :operationType,
                :requestGateway,
                0,
                :zeroAmount,
                0,
                :zeroAmount
            )
            ON CONFLICT (
                subject_key,
                subject_value,
                limit_id,
                limit_details_id,
                period_type,
                operation_type,
                request_gateway
            ) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("subjectKey") String subjectKey,
            @Param("subjectValue") String subjectValue,
            @Param("accountId") String accountId,
            @Param("limitId") Long limitId,
            @Param("limitDetailsId") Long limitDetailsId,
            @Param("tagId") Long tagId,
            @Param("periodType") String periodType,
            @Param("operationType") String operationType,
            @Param("requestGateway") String requestGateway,
            @Param("zeroAmount") BigDecimal zeroAmount
    );
}
