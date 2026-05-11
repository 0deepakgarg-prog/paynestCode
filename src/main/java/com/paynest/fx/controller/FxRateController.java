package com.paynest.fx.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.fx.dto.request.CreateFxRateRequest;
import com.paynest.fx.dto.response.FxRateResponse;
import com.paynest.fx.service.FxRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx-rates")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;

    @PostMapping
    public ResponseEntity<ApiResponse> addFxRate(@Valid @RequestBody CreateFxRateRequest request) {
        FxRateResponse response = fxRateService.addFxRate(request);
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "FX rate created successfully", "fxRate", response)
        );
    }
}
