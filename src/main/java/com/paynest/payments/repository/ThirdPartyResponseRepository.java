package com.paynest.payments.repository;

import com.paynest.payments.entity.ThirdPartyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThirdPartyResponseRepository extends JpaRepository<ThirdPartyResponse, Long> {

    Optional<ThirdPartyResponse> findByTransactionId(String transactionId);
}
