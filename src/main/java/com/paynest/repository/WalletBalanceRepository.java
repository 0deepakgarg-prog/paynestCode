
package com.paynest.repository;

import com.paynest.entity.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletBalanceRepository
        extends JpaRepository<WalletBalance, Long> {

    @Query(value =
            "SELECT * FROM wallet_balance " +
                    "WHERE wallet_id = :walletId " +
                    "FOR UPDATE",
            nativeQuery = true)
    WalletBalance lockBalance(@Param("walletId") Long walletId);

    Optional<WalletBalance> findByWalletId(Long walletId);

}

