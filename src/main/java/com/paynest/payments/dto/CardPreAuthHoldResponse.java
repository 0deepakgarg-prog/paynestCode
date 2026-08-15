package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.CardPreAuthHoldStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class CardPreAuthHoldResponse {
    private String responseStatus;
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String transactionId;
    private String holdId;
    private String cmsTransactionId;
    private Long walletId;
    private String accountId;
    private String currency;
    private String walletType;
    private BigDecimal originalAmount;
    private BigDecimal holdAmount;
    private BigDecimal frozenBalance;
    private CardPreAuthHoldStatus status;
}
