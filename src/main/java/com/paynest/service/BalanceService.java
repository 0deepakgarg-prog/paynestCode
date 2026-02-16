package com.paynest.service;

import com.paynest.dto.BalanceResponse;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletRepository;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final WalletRepository walletRepository;
    private final WalletBalanceRepository balanceRepository;

    public BalanceService(WalletRepository walletRepository,
                          WalletBalanceRepository balanceRepository) {
        this.walletRepository = walletRepository;
        this.balanceRepository = balanceRepository;
    }

    public BalanceResponse getBalance(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO","Wallet not found"));

        WalletBalance balance = balanceRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("INVALID_WALLET_NO","Wallet not found"));

        return new BalanceResponse(
                walletId,
                wallet.getCurrency(),
                balance.getAvailableBalance(),
                balance.getFrozenBalance(),
                balance.getFicBalance()
        );
    }
}
