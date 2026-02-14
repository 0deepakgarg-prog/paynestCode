package com.paynest.controller;

import com.paynest.dto.BalanceResponse;
import com.paynest.service.BalanceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{walletId}/balance")
    public BalanceResponse getBalance(
            @PathVariable Long walletId) {

        return balanceService.getBalance(walletId);
    }
}
