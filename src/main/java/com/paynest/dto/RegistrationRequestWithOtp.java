package com.paynest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationRequestWithOtp {

    @NotBlank(message = "tenantId is required")
    @JsonProperty("tenantId")
    private String tenantId;

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

    }
}

