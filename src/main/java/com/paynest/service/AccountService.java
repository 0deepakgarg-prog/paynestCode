package com.paynest.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.dto.RegistrationRequest;
import com.paynest.entity.Account;
import com.paynest.entity.Enumeration;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.EnumerationRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final EnumerationRepository enumerationRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;

    private final EntityManager entityManager;

    @Transactional
    public Account registerUser(RegistrationRequest request) {

        log.info(enumerationRepository.getSearchPath());
        List<com.paynest.entity.Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<com.paynest.entity.Enumeration> walletTypeList =
                enumerationRepository.findByEnumTypeAndIsActive("WALLET_TYPE",  true);

        if (accountRepository.findByMobileNumber(
                request.getUser().getMobile()).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        Account account = new Account();
        account.setAccountId(IdGenerator.generateAccountId());
        account.setMobileNumber(request.getUser().getMobile());
        account.setAccountType("SUBSCRIBER");
        account.setStatus("ACTIVE");
        accountRepository.save(account);

        List<Wallet> wallets = new ArrayList<>();
        List<WalletBalance> walletBalances = new ArrayList<>();

        for (Enumeration type : walletTypeList) {

            for (Enumeration currency : currencyList) {

                Wallet wallet = new Wallet();
                if(account.getAccountType().equals("SUBSCRIBER") && type.getEnumCode().equals("COMMISSION")){
                    continue;
                }
                wallet.setWalletId(walletRepository.getNextWalletId());
                wallet.setAccountId(account.getAccountId());
                wallet.setCurrency(currency.getEnumCode());
                wallet.setWalletType(type.getEnumCode());
                wallet.setIsDefault(currency.getEnumCode().equals("USD") && type.getEnumCode().equals("MAIN"));
                wallets.add(wallet);
                WalletBalance walletBalance = new WalletBalance();
                walletBalance.setWalletId(wallet.getWalletId());
                walletBalance.setAvailableBalance(BigDecimal.ZERO);
                walletBalance.setFrozenBalance(BigDecimal.ZERO);
                walletBalance.setFicBalance(BigDecimal.ZERO);
                walletBalances.add(walletBalance);
            }
        }

        walletRepository.saveAll(wallets);
        walletBalanceRepository.saveAll(walletBalances);

        return account;
    }
}
