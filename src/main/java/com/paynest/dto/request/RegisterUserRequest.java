package com.paynest.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterUserRequest {

    @NotBlank(message = "requestId is required")
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("user")
    private BusinessAccount user;


    @Data
    public static class BusinessAccount {

        @NotBlank(message = "Mobile number is required")
        @JsonProperty("mobileNumber")
        private String mobileNumber;

        @NotBlank(message = "Account Type is required")
        @JsonProperty("accountType")
        private String accountType;

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

}
