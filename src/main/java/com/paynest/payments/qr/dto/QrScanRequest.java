package com.paynest.payments.qr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QrScanRequest {

    @NotBlank
    private String payload;
}
