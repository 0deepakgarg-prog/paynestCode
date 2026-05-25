package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class GenericServiceExecutionResponse {

    private String serviceCode;

    @JsonIgnore
    private String serviceName;

    private String referenceId;

    private String transactionId;

    private String status;

    private BigDecimal amount;

    private String currency;
}
