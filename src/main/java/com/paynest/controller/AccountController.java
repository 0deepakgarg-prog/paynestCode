package com.paynest.controller;

import com.paynest.dto.*;
import com.paynest.entity.Account;
import com.paynest.service.PinService;
import com.paynest.tenant.TenantContext;
import com.paynest.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;
    private final PinService pinService;


    @PostMapping("/register/selfWithOtp")
    public ResponseEntity<RegistrationResponse> register(
            @RequestBody RegistrationRequestWithOtp request) {

        try {
            log.info("User registration started");
            Account account = accountService.registerUser(request);
            log.info("User registration completed");

            return ResponseEntity.ok(
                    new RegistrationResponse(
                            "SUCCESS",
                            request.getRequestId(),
                            "User registered successfully", account.getAccountId()));

        } finally {
            TenantContext.clear();
            MDC.clear();
        }
    }


    @PostMapping("/register/selfGenOtp")
    public ResponseEntity<RegistrationResponse> registerGenerateOtp(
            @RequestBody RegistrationRequest request) {

        try {
            log.info("Generate Otp for new registration");
            accountService.generateOtpForRegistration(request);
            return ResponseEntity.ok(
                    new RegistrationResponse(
                            "SUCCESS",
                            request.getRequestId(),
                            "OTP generated successfully", null));

        } finally {
            TenantContext.clear();
            MDC.clear();
        }
    }


    @PostMapping("/pin/change")
    public ResponseEntity<?> changePin(
            @Valid @RequestBody ChangePinRequest request) {

        //TODO  : Implement proper authentication and authorization to get accountId
        String accountId = request.getAccountId(); // This should come from authenticated user context

        pinService.changePin(accountId, request);

        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "PIN changed successfully"));
    }


    @PutMapping("/updateSelf")
    public ResponseEntity<?> updateAccount(
            @Valid @RequestBody UpdateAccountRequest request) {

        //TODO  : Implement proper authentication and authorization to get accountId
        String accountId = request.getAccountId(); // This should come from authenticated user context

       accountService.updateAccountDetails(request);

        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "Account Updated successfully"));
    }

}
