package com.paynest.repository;

import com.paynest.entity.TransactionDetails;
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

}
