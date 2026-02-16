package com.paynest.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiErrorResponse {

    private boolean success;
    private String code;
    private String message;
    private LocalDateTime timestamp;

    public ApiErrorResponse(boolean success, String code,
                            String message, LocalDateTime timestamp) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    // getters
}
