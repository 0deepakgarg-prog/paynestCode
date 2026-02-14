package com.paynest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class RegistrationResponse {

    private String status;
    private String requestId;
    private String message;
    private String accountId;

    public RegistrationResponse(String status, String requestId, String message, String accountId) {
        this.status = status;
        this.requestId = requestId;
        this.message = message;
        this.accountId = accountId;
    }

}

