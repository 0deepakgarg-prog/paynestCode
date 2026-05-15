package com.paynest.payments.qr.repository;

import com.paynest.payments.qr.entity.QrPaymentIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QrPaymentIntentRepository extends JpaRepository<QrPaymentIntent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QrPaymentIntent> findFirstByQrIntentId(String qrIntentId);
}
