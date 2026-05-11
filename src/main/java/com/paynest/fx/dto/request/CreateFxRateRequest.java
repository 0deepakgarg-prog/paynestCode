package com.paynest.fx.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateFxRateRequest {

    @NotBlank
    @Size(min = 3, max = 3)
    private String targetCurrency;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 10, fraction = 10)
    private BigDecimal usdRate;

    @Size(max = 20)
    private String rateType;

    @NotBlank
    @Size(max = 50)
    private String provider;

    @NotNull
    private LocalDateTime validFrom;

    @Size(max = 100)
    private String field1;

    @Size(max = 100)
    private String field2;

    @Size(max = 100)
    private String field3;

    @Size(max = 100)
    private String field4;

    @Size(max = 100)
    private String field5;
}
