package com.paynest.service;

import com.paynest.dto.response.BalanceResponse;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@Service
public class BalanceService {

    private final WalletRepository walletRepository;
    private final WalletBalanceRepository balanceRepository;
    private final AccountRepository accountRepo;


    public BalanceResponse getBalance(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO","Wallet not found"));

        WalletBalance balance = balanceRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO","Wallet not found"));

        return new BalanceResponse(
                wallet.getWalletType(),
                wallet.getCurrency(),
                balance.getAvailableBalance(),
                balance.getFrozenBalance(),
                balance.getFicBalance()
        );
    }


    @Transactional
    public List<BalanceResponse> getAllWalletBalance(String accountId) {

        accountRepo.findById(accountId)
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT", "Account not found"));

        List<Wallet> walletList = walletRepository.findByAccountId(accountId);

        return walletList.stream()
                .map(wallet -> {
                    WalletBalance balance = balanceRepository.findById(wallet.getWalletId())
                            .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO", "Wallet not found"));
                    return new BalanceResponse(
                            wallet.getWalletType(),
                            wallet.getCurrency(),
                            balance.getAvailableBalance(),
                            balance.getFrozenBalance(),
                            balance.getFicBalance()
                    );
                })
                .collect(Collectors.toList());
    }



}
