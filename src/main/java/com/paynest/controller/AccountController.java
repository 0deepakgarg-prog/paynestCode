package com.paynest.controller;

import com.paynest.entity.Account;
import com.paynest.tenant.TenantContext;
import com.paynest.service.AccountService;
import com.paynest.dto.RegistrationResponse;
import com.paynest.dto.RegistrationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;


    @PostMapping("/register/self")
    public ResponseEntity<RegistrationResponse> register(
            @RequestBody RegistrationRequest request) {

        try {
            // Set tenant manually (tenant filter skipped)
            //TenantContext.set(request.getTenantId());

            log.info("User registration started");
            log.info(request.getTenantId());
            log.info(request.getRequestId());

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
}
