package com.paynest.payments.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GenericServiceFinancialInfo {

    private BigDecimal amount;

    private String currency;
}
