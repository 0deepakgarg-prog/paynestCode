package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class IntraWalletTransferResponse {

    private TransactionStatus responseStatus;
    private String operationType;
    private String code;
    private String message;
    private Instant timestamp;
    private String traceId;
    private String transactionId;
    private BigDecimal sourceAmount;
    private String sourceWalletType;
    private String sourceCurrency;
    private BigDecimal targetAmount;
    private String targetWalletType;
    private String targetCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal bonusToMainPercentage;
}
