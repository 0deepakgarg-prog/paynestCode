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
    @Query("UPDATE TransactionDetails td SET td.transferStatus = :status WHERE td.id.transactionId = :transactionId")
    int updateTransferStatusByTransactionId(String transactionId, String status);

    @Modifying
    @Transactional
    @Query("""
    UPDATE TransactionDetails td
    SET td.previousBalance = :balanceBefore,
        td.postBalance = :balanceAfter,
        td.previousFicBalance = :ficBefore,
        td.postFicBalance = :ficAfter,
        td.previousFrozenBalance = :frozenBefore,
        td.postFrozenBalance = :frozenAfter,
        td.transferStatus = :transferStatus
    WHERE td.id.transactionId = :txnId
    AND td.id.txnSequenceNumber = :entrySeq
    """)
    int updateBalances(
            String txnId,
            Long entrySeq,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            BigDecimal ficBefore,
            BigDecimal ficAfter,
            BigDecimal frozenBefore,
            BigDecimal frozenAfter,
            String transferStatus
    );
}

