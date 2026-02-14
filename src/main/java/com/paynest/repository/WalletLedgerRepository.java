
package com.paynest.repository;



import com.paynest.entity.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletLedgerRepository
        extends JpaRepository<WalletLedger, Long> {

    // All ledger entries for wallet
    List<WalletLedger> findByWalletId(Long walletId);

    // Ledger entries for transaction
    List<WalletLedger> findByTxnId(String txnId);

    // Ledger entries for account
    List<WalletLedger> findByAccountId(String accountId);

}
