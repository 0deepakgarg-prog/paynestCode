package com.paynest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RegistrationRequest {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("user")
    private UserData user;

    @Data
    public static class UserData {

        @JsonProperty("mobileNumber")
        private String mobile;
    }
}

