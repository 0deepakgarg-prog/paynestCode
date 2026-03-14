package com.paynest.controller;

import com.paynest.dto.response.ApiResponse;
import com.paynest.service.BalanceService;
import com.paynest.service.WalletService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Data
@Slf4j
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final BalanceService balanceService;
    private final WalletService walletService;

    @GetMapping(value="/getAccountWallets/{accountId}", produces = "application/json")
    public ResponseEntity<ApiResponse> getAccountWallet(
            @PathVariable String accountId) {

        Object wallets = walletService.getAccountWallets(accountId);
        ApiResponse response =
                new ApiResponse(
                        "SUCCESS",
                        "Wallets fetched successfully",
                        "wallets", wallets
                );

        return ResponseEntity.ok(response);
    }
}
