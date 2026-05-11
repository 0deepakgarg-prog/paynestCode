package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paynest.enums.RequestGateway;
import com.paynest.users.enums.WalletType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class IntraWalletTransferRequest {

    private RequestGateway requestGateway;

    @JsonProperty("preferredLang")
    private String preferredLang;

    @Size(max = 100)
    private String paymentReference;

    @Size(max = 300)
    private String comments;

    @NotNull
    private Party party;

    private WalletType sourceWalletType;

    private WalletType targetWalletType;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String sourceCurrency;

    @NotBlank
    @Size(min = 3, max = 3)
    private String targetCurrency;

    private Map<String, Object> metadata;

    private Map<String, Object> additionalInfo;
}
