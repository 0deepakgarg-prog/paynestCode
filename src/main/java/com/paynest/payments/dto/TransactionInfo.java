package com.paynest.payments.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionInfo {
    private BigDecimal amount;

    private String currency;

    private String initiatedTransactionId;
}
