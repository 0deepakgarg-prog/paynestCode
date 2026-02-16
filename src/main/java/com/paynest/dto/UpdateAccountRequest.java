package com.paynest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateAccountRequest {

    @JsonProperty("tenantId")
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("user")
    private AccountData user;

    @Data
    public static class AccountData {

        @NotBlank(message = "First Name is required")
        @JsonProperty("firstName")
        private String firstName;

        @NotBlank(message = "Last Name is required")
        @JsonProperty("lastName")
        private String lastName;

        @NotBlank(message = "Email is required")
        @JsonProperty("email")
        private String email;

        @NotBlank(message = "Address is required")
        @JsonProperty("address")
        private String address;

        @NotNull(message = "Gender is required")
        @JsonProperty("gender")
        private Gender gender;

        @NotBlank(message = "Nationality is required")
        @JsonProperty("nationality")
        private String nationality;

        @JsonProperty("ssn")
        private String ssn;

        @NotBlank(message = "Date Of birth is required")
        @JsonProperty("dob")
        private LocalDate dob;

        @JsonProperty("preferredLanguage")
        private String preferredLanguage;

        /*
        @JsonProperty("kycData")
        private KYCData[] kycData;

        public static class KYCData {

            @NotBlank(message = "KYC Type is required")
            @JsonProperty("kycType")
            private String kycType;

            @NotBlank(message = "KYC Value is required")
            @JsonProperty("kycValue")
            private String kycValue;

            @JsonProperty("issueDate")
            private LocalDate issueDate;

            @JsonProperty("expiryDate")
            private LocalDate expiryDate;

            @NotBlank(message = "KYC Id Issue Country is required")
            @JsonProperty("issueCountry")
            private String issueCountry;
            @JsonProperty("isPrimary")
            private boolean isPrimary;

        }
        *
         */

    }
}

