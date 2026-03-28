package com.paynest.payments.repository;

import com.paynest.payments.entity.TransactionDetails;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionDetailsRepository extends JpaRepository<TransactionDetails, String> {

    List<TransactionDetails> findByIdTransactionId(String transactionId);

    @Modifying
    @Transactional
    @Query("""
        UPDATE TransactionDetails td
        SET td.previousBalance = :previousBalance,
            td.postBalance = :postBalance,
            td.previousFicBalance = :previousFicBalance,
            td.postFicBalance = :postFicBalance,
            td.previousFrozenBalance = :previousFrozenBalance,
            td.postFrozenBalance = :postFrozenBalance,
            td.transferStatus = :transferStatus,
            td.transferOn = CURRENT_TIMESTAMP
        WHERE td.id.transactionId = :transactionId
          AND td.id.txnSequenceNumber = :txnSequenceNumber
    """)
    int updateBalances(
            String transactionId,
            Long txnSequenceNumber,
            BigDecimal previousBalance,
            BigDecimal postBalance,
            BigDecimal previousFicBalance,
            BigDecimal postFicBalance,
            BigDecimal previousFrozenBalance,
            BigDecimal postFrozenBalance,
            String transferStatus
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE TransactionDetails td
        SET td.transferStatus = :transferStatus,
            td.transferOn = CURRENT_TIMESTAMP
        WHERE td.id.transactionId = :transactionId
    """)
    int updateTransferStatusByTransactionId(String transactionId, String transferStatus);
}
