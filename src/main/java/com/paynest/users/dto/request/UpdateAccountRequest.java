package com.paynest.users.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.users.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateAccountRequest {

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

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
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dob;

        @JsonProperty("preferredLang")
        private String preferredLanguage;

        @JsonProperty("attr1")
        private String attr1;

        @JsonProperty("attr2")
        private String attr2;

        @JsonProperty("attr3")
        private String attr3;

        @JsonProperty("attr4")
        private String attr4;

        @JsonProperty("attr5")
        private String attr5;

        @JsonProperty("attr6")
        private String attr6;

        @JsonProperty("attr7")
        private String attr7;

        @JsonProperty("attr8")
        private String attr8;

        @JsonProperty("attr9")
        private String attr9;

        @JsonProperty("attr10")
        private String attr10;

    }
}


