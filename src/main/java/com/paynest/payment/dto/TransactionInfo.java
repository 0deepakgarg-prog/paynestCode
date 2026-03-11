package com.paynest.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionInfo {
    private BigDecimal amount;

    private String currency;
}