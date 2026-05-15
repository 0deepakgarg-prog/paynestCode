package com.paynest.payments.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.payments.dto.PasscodeDetailsRequest;
import com.paynest.payments.dto.PasscodeDetailsResponse;
import com.paynest.payments.service.PasscodeQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passcode")
@RequiredArgsConstructor
public class PasscodeController {

    private final PasscodeQueryService passcodeQueryService;

    @PostMapping("/details")
    public ResponseEntity<ApiResponse> getPasscodeDetails(
            @Valid @RequestBody PasscodeDetailsRequest request) {
        PasscodeDetailsResponse response = passcodeQueryService.getPasscodeDetails(request.getPasscode());
        return ResponseEntity.ok(
                new ApiResponse("SUCCESS", "Passcode details fetched successfully", "passcodeDetails", response)
        );
    }
}
