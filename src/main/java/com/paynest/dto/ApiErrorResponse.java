package com.paynest.dto;

import com.paynest.enums.TransactionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiErrorResponse {

    private TransactionStatus responseStatus;
    private String code;
    private String message;
    private String traceId;
    private LocalDateTime timestamp;

    public ApiErrorResponse(TransactionStatus responseStatus, String code,
                            String message, String traceId, LocalDateTime timestamp) {
        this.responseStatus = responseStatus;
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    // getters
}
