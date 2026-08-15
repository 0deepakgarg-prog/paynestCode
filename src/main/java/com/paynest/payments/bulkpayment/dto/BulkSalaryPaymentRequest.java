package com.paynest.payments.bulkpayment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BulkSalaryPaymentRequest {

    @Size(max = 100)
    private String batchReference;

    @NotBlank
    @Size(max = 30)
    private String batchType;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal totalAmount;

    @NotBlank
    @Size(max = 10)
    private String currency;

    @NotBlank
    @Size(max = 30)
    private String debitorAccountId;

    @NotBlank
    @Size(max = 50)
    private String debitorWalletType;

    @NotBlank
    @Size(max = 10)
    private String debitorCurrency;

    @Size(max = 500)
    private String remarks;

    private JsonNode additionalInfo;

    @Valid
    @NotEmpty
    @Size(max = 1000)
    private List<BulkSalaryPaymentEntryRequest> payments;
}
