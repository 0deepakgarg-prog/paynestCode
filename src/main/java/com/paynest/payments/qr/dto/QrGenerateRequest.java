package com.paynest.payments.qr.dto;

import com.paynest.payments.qr.enums.QrType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QrGenerateRequest {

    @NotNull
    private QrType qrType;

    @NotBlank
    private String operationType;

    @Valid
    @NotNull
    private QrCreditor creditor;

    @NotBlank
    private String currency;

    @Positive
    private BigDecimal amount;

    private Integer expiresInMinutes;
}
