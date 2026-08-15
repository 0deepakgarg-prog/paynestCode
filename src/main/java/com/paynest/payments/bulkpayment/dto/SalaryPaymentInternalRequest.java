package com.paynest.payments.bulkpayment.dto;

import com.paynest.enums.RequestGateway;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalaryPaymentInternalRequest {

    @Size(max = 30)
    private String batchId;

    @Size(max = 30)
    private String batchDetailId;

    @NotBlank
    @Size(max = 30)
    private String creditorIdentifierType;

    @NotBlank
    @Size(max = 50)
    private String creditorIdentifierValue;

    @NotBlank
    @Size(max = 50)
    private String creditorWalletType;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 10)
    private String currency;

    private RequestGateway requestGateway;

    @Size(max = 10)
    private String preferredLang;

    @Size(max = 100)
    private String paymentReference;

    @Size(max = 300)
    private String comments;
}
