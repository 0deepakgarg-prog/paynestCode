package com.paynest.users.repository;

import com.paynest.users.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    Optional<Otp> findTopByMobileNumberAndReferenceTypeAndStatusOrderByCreatedAtDesc(
            String mobileNumber,
            String referenceType,
            String status
    );

    Optional<Otp> findTopByReferenceIdAndReferenceTypeAndStatusOrderByCreatedAtDesc(
            String referenceId,
            String referenceType,
            String status
    );

    Optional<Otp> findByOtpValue(
            int otpValue
    );

    long countByMobileNumberAndCreatedAtAfter(
            String mobileNumber,
            LocalDateTime time
    );
}

