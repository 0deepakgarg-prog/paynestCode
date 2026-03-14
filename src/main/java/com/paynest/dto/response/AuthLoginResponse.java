package com.paynest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthLoginResponse {
    private String status;
    private String message;
    private String accountId;
    private String tokenType;
    private String accessToken;
    private long expiresInSeconds;
}
