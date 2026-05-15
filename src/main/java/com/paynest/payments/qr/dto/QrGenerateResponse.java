package com.paynest.payments.qr.dto;

import com.paynest.payments.qr.enums.QrType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QrGenerateResponse {
    private QrType qrType;
    private String qrIntentId;
    private String operationType;
    private String payload;
    private String qrImageBase64;
    private LocalDateTime expiresAt;
}
