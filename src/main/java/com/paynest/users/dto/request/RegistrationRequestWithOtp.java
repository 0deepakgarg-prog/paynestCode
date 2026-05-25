package com.paynest.users.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRequestWithOtp {

    @NotBlank(message = "requestId is required")
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("user")
    private UserData user;

    @Data
    public static class UserData {

        @NotBlank(message = "Mobile number is required")
        @JsonProperty("mobileNumber")
        private String mobile;

        @NotNull(message = "OTP is required")
        @JsonProperty("otp")
        private String otp;

        @Size(max = 100, message = "Account code must not exceed 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Account code must be alphanumeric")
        @JsonProperty("accountCode")
        private String accountCode;

    }
}


