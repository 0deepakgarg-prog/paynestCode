package com.paynest.payments.repository;

import com.paynest.payments.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional;


@Repository
public interface TransactionsRepository extends JpaRepository<Transactions, String> {

    Transactions findByTransactionId(String transactionId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Transactions t
        SET t.transferStatus = :status,
            t.errorCode = :errorCode,
            t.modifiedOn = CURRENT_TIMESTAMP
        WHERE t.transactionId = :txnId
    """)
    void updateStatus(String txnId,String status,String errorCode);


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

}

