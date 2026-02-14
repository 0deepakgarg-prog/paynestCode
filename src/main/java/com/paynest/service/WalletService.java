
package com.paynest.service;

import com.paynest.entity.WalletBalance;
import com.paynest.entity.WalletLedger;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletLedgerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {


    private final WalletBalanceRepository balanceRepo;
    private final WalletLedgerRepository ledgerRepo;

    @Transactional
    public void debitWallet(Long walletId,
                            BigDecimal amount,
                            String txnId) throws ApplicationException {

        WalletBalance balance =
                balanceRepo.lockBalance(walletId);

        if (balance.getAvailableBalance()
                .compareTo(amount) < 0) {
            throw new ApplicationException("Insufficient balance");
        }

        BigDecimal before =
                balance.getAvailableBalance();

        BigDecimal after =
                before.subtract(amount);

        // 3. Insert ledger entry
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(txnId);
        ledger.setWalletId(walletId);
        ledger.setEntryType("DR");
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);

        ledgerRepo.save(ledger);
        balance.setAvailableBalance(after);
        balanceRepo.save(balance);
    }

    @Transactional
    public void creditWallet(Long walletId,
                            BigDecimal amount,
                            String txnId) {

        WalletBalance balance =
                balanceRepo.lockBalance(walletId);

        BigDecimal before =
                balance.getAvailableBalance();

        BigDecimal after =
                before.add(amount);

        // 3. Insert ledger entry
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(txnId);
        ledger.setWalletId(walletId);
        ledger.setEntryType("CR");
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);

        ledgerRepo.save(ledger);
        balance.setAvailableBalance(after);
        balanceRepo.save(balance);
    }


}
