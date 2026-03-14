
package com.paynest.service;

import com.paynest.dto.response.AccountWalletBalancesResponse;
import com.paynest.dto.response.BalanceResponse;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.entity.WalletLedger;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletLedgerRepository;
import com.paynest.repository.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.paynest.security.JWTUtils.getCurrentAccountId;
import static com.paynest.security.JWTUtils.getCurrentAccountType;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletBalanceRepository balanceRepo;
    private final AccountRepository accountRepo;
    private final WalletLedgerRepository ledgerRepo;
    private final WalletRepository walletRepository;

    @Transactional
    public void debitWallet(Wallet wallet,
                            BigDecimal amount,
                            String txnId) throws ApplicationException {

        WalletBalance balance =
                balanceRepo.lockBalance(wallet.getWalletId());

        if (balance.getAvailableBalance()
                .compareTo(amount) < 0) {
            throw new ApplicationException("INSUFFICIENT_BALANCE","Insufficient balance");
        }

        BigDecimal before =
                balance.getAvailableBalance();

        BigDecimal after =
                before.subtract(amount);

        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(txnId);
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setCurrency(wallet.getCurrency());
        ledger.setEntryType("DR");
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);

        ledgerRepo.save(ledger);
        balance.setAvailableBalance(after);
        balanceRepo.save(balance);
    }

    @Transactional
    public void creditWallet(Wallet wallet,
                            BigDecimal amount,
                            String txnId) {

        WalletBalance balance =
                balanceRepo.lockBalance(wallet.getWalletId());

        BigDecimal before =
                balance.getAvailableBalance();

        BigDecimal after =
                before.add(amount);

        // 3. Insert ledger entry
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(txnId);
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setCurrency(wallet.getCurrency());
        ledger.setEntryType("CR");
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);

        ledgerRepo.save(ledger);
        balance.setAvailableBalance(after);
        balanceRepo.save(balance);
    }

    @Transactional
    public List<Wallet> getWalletsByAccountId(String accountId) {
        accountRepo.findById(accountId)
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT", "Account not found"));

        return walletRepository.findByAccountId(accountId);
    }

    @Transactional
    public AccountWalletBalancesResponse getAccountWallets(String accountId) {

        if(!accountId.equalsIgnoreCase(getCurrentAccountId()) &&
                !getCurrentAccountType().equalsIgnoreCase("ADMIN")){
            throw new ApplicationException("INVALID_PRIVILEGES", "Token does not have necessary access");
        }

        List<Wallet> wallets = getWalletsByAccountId(accountId);
        List<BalanceResponse> balances = wallets.stream()
                .map(wallet -> {
                    WalletBalance balance = balanceRepo.findById(wallet.getWalletId())
                            .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO", "Wallet not found"));
                    return new BalanceResponse(
                            wallet.getWalletType(),
                            wallet.getCurrency(),
                            balance.getAvailableBalance(),
                            balance.getFrozenBalance(),
                            balance.getFicBalance()
                    );
                })
                .collect(java.util.stream.Collectors.toList());
        return new AccountWalletBalancesResponse(accountId, balances);
    }

}
