package com.paynest.payments.repository;

import com.paynest.payments.entity.Transactions;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionsRepository extends JpaRepository<Transactions, String> {

    Transactions findByTransactionId(String transactionId);

    Optional<Transactions> findFirstByTraceId(String traceId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Transactions t
        SET t.transferStatus = :status,
            t.errorCode = :errorCode,
            t.modifiedOn = CURRENT_TIMESTAMP
        WHERE t.transactionId = :txnId
    """)
    void updateStatus(String txnId, String status, String errorCode);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE transactions
        SET metadata = COALESCE(metadata, '{}'::jsonb) || CAST(:metadata AS jsonb)
        WHERE transaction_id = :txnId
    """, nativeQuery = true)
    void updateMetadata(String txnId, String metadata);

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE transactions
    SET third_party_data = COALESCE(third_party_data, '{}'::jsonb) || CAST(:thirdPartyData AS jsonb)
    WHERE transaction_id = :txnId
""", nativeQuery = true)
    void updateThirdPartyData(String txnId, String thirdPartyData);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Transactions t
        SET t.paymentReference = :paymentReference
        WHERE t.transactionId = :txnId
    """)
    int updatePaymentReference(String txnId, String paymentReference);

    @Modifying
    @Transactional
    @Query("""
    UPDATE Transactions t
    SET t.comments = :comments
    WHERE t.transactionId = :txnId
""")
    int updateComments(String txnId, String comments);

    @Modifying
    @Transactional
    @Query("""
    UPDATE Transactions t
    SET t.field1 = :comments
    WHERE t.transactionId = :txnId
""")
    int updateApproveOrRejectComments(String txnId, String comments);

    @Query("""
        SELECT COALESCE(SUM(t.transactionValue), 0)
        FROM Transactions t
        WHERE t.serviceCode = :serviceCode
          AND t.debtorAccountId = :userId
          AND t.createdOn >= :fromDateTime
    """)
    BigDecimal sumTransactionValueByServiceCodeSince(String serviceCode, String userId, LocalDateTime fromDateTime);

    @Query("""
        SELECT COUNT(t)
        FROM Transactions t
        WHERE t.serviceCode = :serviceCode
          AND t.debtorAccountId = :userId
          AND t.createdOn >= :fromDateTime
    """)
    long countTransactionsByServiceCodeSince(String serviceCode, String userId, LocalDateTime fromDateTime);

    @Query("""
        SELECT COALESCE(SUM(t.transactionValue), 0)
        FROM Transactions t
        WHERE t.serviceCode = :serviceCode
          AND t.creditorAccountId = :userId
          AND t.createdOn >= :fromDateTime
    """)
    BigDecimal sumTransactionValueByServiceCodeSinceForCreditor(
            String serviceCode,
            String userId,
            LocalDateTime fromDateTime
    );

    @Query("""
        SELECT COUNT(t)
        FROM Transactions t
        WHERE t.serviceCode = :serviceCode
          AND t.creditorAccountId = :userId
          AND t.createdOn >= :fromDateTime
    """)
    long countTransactionsByServiceCodeSinceForCreditor(
            String serviceCode,
            String userId,
            LocalDateTime fromDateTime
    );
}
