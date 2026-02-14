package com.paynest.repository;


import com.paynest.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    List<Wallet> findByAccountId(String accountId);

    Optional<Wallet> findByAccountIdAndCurrencyAndWalletType(
            String accountId,
            String currency,
            String walletType
    );

    Optional<Wallet> findByAccountIdAndIsDefaultTrue(String accountId);

    List<Wallet> findByAccountIdAndStatus(
            String accountId,
            String status
    );

    @Query(value = "SELECT nextval('wallet_wallet_id_seq')",
            nativeQuery = true)
    Long getNextWalletId();

}
