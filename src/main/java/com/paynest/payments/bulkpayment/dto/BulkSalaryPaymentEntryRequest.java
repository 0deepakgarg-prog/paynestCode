package com.paynest.payments.bulkpayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BulkSalaryPaymentEntryRequest {

    @Size(max = 100)
    private String itemReference;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 10)
    private String currency;

    @NotBlank
    @Size(max = 50)
    private String creditorWalletType;

    @NotBlank
    @Size(max = 10)
    private String creditorCurrency;

    @NotBlank
    @Size(max = 30)
    private String creditorIdentifierType;

    @NotBlank
    @Size(max = 50)
    private String creditorIdentifierValue;

    @Size(max = 100)
    private String paymentReference;

    @Size(max = 300)
    private String comments;

    private JsonNode additionalInfo;
}
