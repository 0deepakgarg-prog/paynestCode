package com.paynest.users.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.users.dto.request.WalletRestrictionRequest;
import com.paynest.users.dto.response.WalletRestrictionHistoryResponse;
import com.paynest.users.dto.response.WalletRestrictionResponse;
import com.paynest.users.service.WalletRestrictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet/restrictions")
@RequiredArgsConstructor
public class WalletRestrictionController {

    private final WalletRestrictionService walletRestrictionService;

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse> getWalletRestriction(@PathVariable Long walletId) {
        WalletRestrictionResponse restriction = walletRestrictionService.getWalletRestriction(walletId);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Wallet restriction fetched successfully",
                "walletRestriction",
                restriction
        ));
    }

    @GetMapping("/{walletId}/history")
    public ResponseEntity<ApiResponse> getWalletRestrictionHistory(@PathVariable Long walletId) {
        List<WalletRestrictionHistoryResponse> history = walletRestrictionService.getWalletRestrictionHistory(walletId);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Wallet restriction history fetched successfully",
                "walletRestrictionHistory",
                history
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addWalletRestriction(@Valid @RequestBody WalletRestrictionRequest request) {
        WalletRestrictionResponse restriction = walletRestrictionService.addWalletRestriction(request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Wallet restriction added successfully",
                "walletRestriction",
                restriction
        ));
    }

    @PutMapping("/{walletId}")
    public ResponseEntity<ApiResponse> updateWalletRestriction(
            @PathVariable Long walletId,
            @Valid @RequestBody WalletRestrictionRequest request) {
        WalletRestrictionResponse restriction = walletRestrictionService.updateWalletRestriction(walletId, request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Wallet restriction updated successfully",
                "walletRestriction",
                restriction
        ));
    }
}
