
package com.paynest.users.service;

import com.paynest.common.ErrorCodes;
import com.paynest.users.dto.response.AccountWalletBalancesResponse;
import com.paynest.users.dto.response.BalanceResponse;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.users.repository.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.paynest.config.security.JWTUtils.getCurrentAccountId;
import static com.paynest.config.security.JWTUtils.getCurrentAccountType;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletBalanceRepository balanceRepo;
    private final AccountRepository accountRepo;
    private final WalletLedgerRepository ledgerRepo;
    private final WalletRepository walletRepository;
    private final WalletCacheService walletCacheService;

    @Transactional
    public void debitWallet(Wallet wallet,
                            BigDecimal amount,
                            String txnId) throws ApplicationException {

        WalletBalance balance =
                balanceRepo.lockBalance(wallet.getWalletId());

        //DO not check the wallet balance in case of system wallets and bank and commdis
        if(!(wallet.getWalletType().equalsIgnoreCase("BANK") ||
                wallet.getWalletType().equalsIgnoreCase("COMMDIS")) ) {
            if (balance.getAvailableBalance()
                    .compareTo(amount) < 0) {
                throw new ApplicationException(ErrorCodes.INSUFFICIENT_BALANCE, "Insufficient balance");
            }
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
        walletCacheService.refreshAccountWallets(wallet.getAccountId());
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
        walletCacheService.refreshAccountWallets(wallet.getAccountId());
    }

    @Transactional
    public List<Wallet> getWalletsByAccountId(String accountId) {
        accountRepo.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        return walletRepository.findByAccountId(accountId);
    }

    @Transactional
    public AccountWalletBalancesResponse getAccountWallets(String accountId) {

        if(!accountId.equalsIgnoreCase(getCurrentAccountId()) &&
                !getCurrentAccountType().equalsIgnoreCase("ADMIN")){
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        return walletCacheService.getCachedAccountWallets(accountId)
                .orElseGet(() -> walletCacheService.refreshAccountWallets(accountId));
    }

}

