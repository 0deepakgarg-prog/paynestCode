package com.paynest.payments.repository;

import com.paynest.payments.entity.CardPreAuthHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardPreAuthHoldRepository extends JpaRepository<CardPreAuthHold, String> {

    boolean existsByCmsTransactionId(String cmsTransactionId);

    Optional<CardPreAuthHold> findByCmsTransactionId(String cmsTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CardPreAuthHold> findFirstByHoldId(String holdId);
}
