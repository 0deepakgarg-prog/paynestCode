package com.paynest.users.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class RegisterUserRequest {

    @NotBlank(message = "requestId is required")
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("user")
    private BusinessAccount user;

    @JsonProperty("billerInfo")
    private BillerInfo billerInfo;

    @JsonProperty("merchantInfo")
    private MerchantInfo merchantInfo;


    @Data
    public static class BusinessAccount {

        @NotBlank(message = "Mobile number is required")
        @JsonProperty("mobileNumber")
        private String mobileNumber;

        @NotBlank(message = "Account Type is required")
        @JsonProperty("accountType")
        private String accountType;

        @NotBlank(message = "Account code is required")
        @Size(max = 100, message = "Account code must not exceed 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Account code must be alphanumeric")
        @JsonProperty("accountCode")
        private String accountCode;

        @NotBlank(message = "First Name is required")
        @JsonProperty("firstName")
        private String firstName;

        @NotBlank(message = "Last Name is required")
        @JsonProperty("lastName")
        private String lastName;

        @JsonProperty("email")
        private String email;

        @JsonProperty("address")
        private String address;

        @JsonProperty("gender")
        private String gender;

        @JsonProperty("dateOfBirth")
        @Past(message = "Date of birth must be in the past")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dateOfBirth;

        @JsonProperty("preferredLang")
        private String preferredLang;

        @JsonProperty("nationality")
        private String nationality;

        @JsonProperty("ssn")
        private String ssn;

        @JsonProperty("remarks")
        private String remarks;

        @JsonProperty("loginId")
        private String loginId;

        @JsonProperty("role")
        private String role;

    }

    @Data
    public static class BillerInfo {

        @JsonProperty("billerCategory")
        private String billerCategory;

        @JsonProperty("billerCode")
        private String billerCode;

        @JsonProperty("billerSubCategory")
        private String billerSubCategory;

        @JsonProperty("billerConfig")
        private JsonNode billerConfig;

        @JsonProperty("billerSettings")
        private JsonNode billerSettings;
    }

    @Data
    public static class MerchantInfo {

        @JsonProperty("merchantCode")
        private String merchantCode;

        @JsonProperty("mccCodes")
        private List<String> mccCodes;

        @JsonProperty("merchantConfig")
        private JsonNode merchantConfig;
    }

}

