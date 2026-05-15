package com.paynest.payments.repository;

import com.paynest.payments.entity.Passcode;
import com.paynest.payments.enums.PasscodeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasscodeRepository extends JpaRepository<Passcode, Long> {

    boolean existsByPasscode(String passcode);

    Optional<Passcode> findByPasscode(String passcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Passcode> findByPasscodeAndUnregisteredMsisdnAndStatus(
            String passcode,
            String unregisteredMsisdn,
            PasscodeStatus status
    );
}
