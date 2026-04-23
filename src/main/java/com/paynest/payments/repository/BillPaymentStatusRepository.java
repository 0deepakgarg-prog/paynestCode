package com.paynest.payments.repository;

import com.paynest.payments.entity.BillPaymentStatusRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillPaymentStatusRepository extends JpaRepository<BillPaymentStatusRecord, String> {
}
