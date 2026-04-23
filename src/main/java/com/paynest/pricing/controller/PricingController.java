package com.paynest.pricing.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.pricing.dto.request.CreatePricingRuleRequest;
import com.paynest.pricing.dto.request.UpdatePricingStatusRequest;
import com.paynest.pricing.dto.response.PricingRuleResponse;
import com.paynest.pricing.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Slf4j
public class PricingController {

    private final PricingService pricingService;

    @PostMapping
    public ResponseEntity<ApiResponse> addPricingRule(
            @Valid @RequestBody CreatePricingRuleRequest request) {
        PricingRuleResponse response = pricingService.addPricingRule(request);
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "Pricing rule created successfully", "pricing", response)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllPricingRules() {
        List<PricingRuleResponse> response = pricingService.getAllPricingRules();
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "Pricing rules fetched successfully", "pricingRules", response)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getPricingRule(@PathVariable Long id) {
        PricingRuleResponse response = pricingService.getPricingRule(id);
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "Pricing rule fetched successfully", "pricing", response)
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse> updatePricingStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePricingStatusRequest request) {
        PricingRuleResponse response = pricingService.updatePricingStatus(id, request);
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "Pricing rule status updated successfully", "pricing", response)
        );
    }
}
