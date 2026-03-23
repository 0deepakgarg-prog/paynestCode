package com.paynest.users.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistrationRequest {

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;


    @JsonProperty("user")
    private UserData user;

    @Data
    public static class UserData {

        @NotBlank(message = "Mobile number is required")
        @JsonProperty("mobileNumber")
        private String mobile;
    }
}


