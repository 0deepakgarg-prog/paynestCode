package com.paynest.users.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.exception.ApplicationException;
import com.paynest.users.dto.response.AccountKycDetailsResponse;
import com.paynest.users.dto.request.*;
import com.paynest.users.dto.response.RegistrationResponse;
import com.paynest.users.entity.Account;
import com.paynest.users.service.PinService;
import com.paynest.users.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        log.info("User registration started");
        Account account = accountService.registerUser(request);
        log.info("User registration completed");

        return ResponseEntity.ok(
                new RegistrationResponse(
                        "SUCCESS",
                        request.getRequestId(),
                        "User registered successfully", account.getAccountId()));
    }

    @PostMapping("/registerUser")
    public ResponseEntity<RegistrationResponse> registerAdmin(
            @RequestBody RegisterUserRequest accountRequest) {
        return registerByRole(accountRequest);
    }

    private ResponseEntity<RegistrationResponse> registerByRole(
            RegisterUserRequest accountRequest) {
        log.info(
                "RegisterUser request received. requestId={}, accountType={}, role={}, loginId={}, mobile={}",
                accountRequest != null ? accountRequest.getRequestId() : null,
                accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getAccountType() : null,
                accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getRole() : null,
                accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getLoginId() : null,
                accountRequest != null && accountRequest.getUser() != null ? maskMobile(accountRequest.getUser().getMobileNumber()) : null
        );
        try {
            Account account = accountService.registerAccountByRole(accountRequest);
            log.info(
                    "RegisterUser request completed. requestId={}, accountId={}, accountType={}, role={}",
                    accountRequest != null ? accountRequest.getRequestId() : null,
                    account.getAccountId(),
                    account.getAccountType(),
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getRole() : null
            );

            return ResponseEntity.ok(
                    new RegistrationResponse(
                            "SUCCESS",
                            accountRequest.getRequestId(),
                            "User registered successfully",
                            account.getAccountId()));
        } catch (ApplicationException ex) {
            log.error(
                    "RegisterUser request failed with application error. requestId={}, accountType={}, role={}, loginId={}, mobile={}, errorCode={}, errorMessage={}, params={}",
                    accountRequest != null ? accountRequest.getRequestId() : null,
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getAccountType() : null,
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getRole() : null,
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getLoginId() : null,
                    accountRequest != null && accountRequest.getUser() != null ? maskMobile(accountRequest.getUser().getMobileNumber()) : null,
                    ex.getErrorCode(),
                    ex.getErrorMessage(),
                    ex.getParams(),
                    ex
            );
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "RegisterUser request failed unexpectedly. requestId={}, accountType={}, role={}, loginId={}, mobile={}, error={}",
                    accountRequest != null ? accountRequest.getRequestId() : null,
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getAccountType() : null,
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getRole() : null,
                    accountRequest != null && accountRequest.getUser() != null ? accountRequest.getUser().getLoginId() : null,
                    accountRequest != null && accountRequest.getUser() != null ? maskMobile(accountRequest.getUser().getMobileNumber()) : null,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private String maskMobile(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.length() <= 4) {
            return mobileNumber;
        }
        return "****" + mobileNumber.substring(mobileNumber.length() - 4);
    }


    @PostMapping("/register/selfGenOtp")
    public ResponseEntity<RegistrationResponse> registerGenerateOtp(
            @RequestBody RegistrationRequest request) {
        log.info("Generate Otp for new registration");
        accountService.generateOtpForRegistration(request);
        return ResponseEntity.ok(
                new RegistrationResponse(
                        "SUCCESS",
                        request.getRequestId(),
                        "OTP generated successfully", null));
    }

    @PostMapping("/pin/changeDefault")
    public ResponseEntity<?> changeDefaultPin(
            @Valid @RequestBody ChangePinRequest request) {
        pinService.changePin(request, false);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "PIN changed successfully"));
    }

    @PostMapping("/pin/change")
    public ResponseEntity<?> changePin(
            @Valid @RequestBody ChangePinRequest request) {
        pinService.changePin(request, true);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "PIN changed successfully"));
    }

    @PostMapping("/password/change")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        pinService.changePassword(request, true);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "Password changed successfully"));
    }

    @PostMapping("/password/changeDefault")
    public ResponseEntity<?> changeDefaultPassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        pinService.changePassword(request, false);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "Password changed successfully"));
    }


    @PutMapping("/updateSelf")
    public ResponseEntity<?> updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        accountService.updateAccountDetails(request);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "Account Updated successfully"));
    }

    @PostMapping("/addKyc")
    public ResponseEntity<?> addAccountKyc(
            @Valid @RequestBody AddAccountKycRequest request) {
        log.info("inside addAccountKyc");
        accountService.updateAccountKycDetails(request);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "KYC update request received, Pending for Approval"));
    }

    @GetMapping("/getAccountDetails/{accountId}")
    public ResponseEntity<ApiResponse> getAccountDetails(@PathVariable String accountId) {
        log.info("inside fetch account details");
        AccountKycDetailsResponse response = accountService.getAccountWithKycDetails(accountId);
        log.info("inside fetch account details response : " + response);
        ApiResponse apiResponse =
                new ApiResponse(
                        "SUCCESS",
                        "Account fetched successfully",
                        "account", response
                );

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/subscriber/{accountId}")
    public ResponseEntity<?> deleteSubscriber(@PathVariable String accountId) {
        accountService.deleteSubscriber(accountId);
        return ResponseEntity.ok(
                Map.of("status", "SUCCESS", "message", "Subscriber deactivated successfully"));
    }

}

