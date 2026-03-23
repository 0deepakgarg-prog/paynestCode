package com.paynest.users.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChallengeTokenResponse {
    private String status;
    private String message;
    private String accountId;
    private String tokenType;
    private String challengeToken;
    private long expiresInSeconds;
}

