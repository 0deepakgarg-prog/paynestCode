package com.paynest.limits.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.limits.dto.request.UpdateTransactionLimitStatusRequest;
import com.paynest.limits.dto.request.UpsertTransactionLimitProfileRequest;
import com.paynest.limits.dto.response.TransactionLimitProfileResponse;
import com.paynest.limits.dto.response.TransactionLimitProfileSummaryResponse;
import com.paynest.limits.dto.response.TransactionLimitReferenceDataResponse;
import com.paynest.limits.dto.response.TransactionLimitUsageResponse;
import com.paynest.limits.service.TransactionLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transaction-limits")
@RequiredArgsConstructor
public class TransactionLimitController {

    private final TransactionLimitService transactionLimitService;

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse> listProfiles(
            @RequestParam(required = false) String limitType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String walletType,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String subjectKey
    ) {
        List<TransactionLimitProfileSummaryResponse> response = transactionLimitService.listProfiles(
                limitType,
                status,
                tagId,
                walletType,
                currency,
                subjectKey
        );
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit profiles fetched successfully",
                "limitProfiles",
                response
        ));
    }

    @PostMapping("/profiles")
    public ResponseEntity<ApiResponse> createProfile(
            @Valid @RequestBody UpsertTransactionLimitProfileRequest request
    ) {
        TransactionLimitProfileResponse response = transactionLimitService.createProfile(request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit profile created successfully",
                "limitProfile",
                response
        ));
    }

    @GetMapping("/profiles/{limitId}")
    public ResponseEntity<ApiResponse> getProfile(@PathVariable Long limitId) {
        TransactionLimitProfileResponse response = transactionLimitService.getProfile(limitId);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit profile fetched successfully",
                "limitProfile",
                response
        ));
    }

    @PutMapping("/profiles/{limitId}")
    public ResponseEntity<ApiResponse> updateProfile(
            @PathVariable Long limitId,
            @Valid @RequestBody UpsertTransactionLimitProfileRequest request
    ) {
        TransactionLimitProfileResponse response = transactionLimitService.updateProfile(limitId, request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit profile updated successfully",
                "limitProfile",
                response
        ));
    }

    @PatchMapping("/profiles/{limitId}/status")
    public ResponseEntity<ApiResponse> updateProfileStatus(
            @PathVariable Long limitId,
            @Valid @RequestBody UpdateTransactionLimitStatusRequest request
    ) {
        TransactionLimitProfileResponse response = transactionLimitService.updateStatus(limitId, request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit profile status updated successfully",
                "limitProfile",
                response
        ));
    }

    @DeleteMapping("/profiles/{limitId}")
    public ResponseEntity<?> deleteProfile(@PathVariable Long limitId) {
        transactionLimitService.deleteProfile(limitId);
        return ResponseEntity.ok(Map.of(
                "status",
                "SUCCESS",
                "message",
                "Transaction limit profile deleted successfully"
        ));
    }

    @GetMapping("/utilization")
    public ResponseEntity<ApiResponse> getUtilization(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String identifierType,
            @RequestParam(required = false) String identifierValue,
            @RequestParam(required = false) String periodType
    ) {
        List<TransactionLimitUsageResponse> response = transactionLimitService.getUtilization(
                accountId,
                identifierType,
                identifierValue,
                periodType
        );
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit utilization fetched successfully",
                "limitUtilization",
                response
        ));
    }

    @GetMapping("/reference-data")
    public ResponseEntity<ApiResponse> getReferenceData() {
        TransactionLimitReferenceDataResponse response = transactionLimitService.getReferenceData();
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                "Transaction limit reference data fetched successfully",
                "referenceData",
                response
        ));
    }
}
