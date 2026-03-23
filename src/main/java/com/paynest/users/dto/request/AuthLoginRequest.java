package com.paynest.users.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginRequest {

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

    @JsonProperty("user")
    private User user;

    @JsonProperty("authFactor")
    private AuthFactor authFactor;

    @Data
    public static class User {
        @NotBlank(message = "identifierType is required")
        private String identifierType;

        @NotBlank(message = "identifierValue is required")
        private String identifierValue;
    }


    @Data
    public static class AuthFactor {
        @NotBlank(message = "identifierType is required")
        private String authType;

        @NotBlank(message = "identifierValue is required")
        private String credential;
    }
}

