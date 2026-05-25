package com.paynest.config.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.config.dto.response.EnumerationResponse;
import com.paynest.config.service.EnumerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enumerations")
@RequiredArgsConstructor
public class EnumerationController {

    private final EnumerationService enumerationService;

    @GetMapping("/{enumType}")
    public ResponseEntity<ApiResponse> getEnumerationsByType(@PathVariable String enumType) {
        List<EnumerationResponse> response = enumerationService.getActiveEnumerationsByType(enumType);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Enumerations fetched successfully", "enumerations", response));
    }
}
