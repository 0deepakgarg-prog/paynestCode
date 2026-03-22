package com.paynest.repository;

import com.paynest.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional;

import java.util.Optional;


@Repository
public interface TransactionsRepository extends JpaRepository<Transactions, String> {

    Transactions findByTransactionId(String transactionId);

    Optional<Transactions> findFirstByTraceId(String traceId);

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
}
