package com.paynest.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryTransactionResponse {

    private String transactionId;
    private String transferOn;
    private String serviceCode;
    private String serviceName;
    private String status;
    private String errorCode;
    private String entryType;
    private BigDecimal transactionAmount;
    private BigDecimal approvedAmount;
    private BigDecimal requestedAmount;
    private BigDecimal previousBalance;
    private BigDecimal postBalance;
    private Long walletId;
    private String walletType;
    private String currency;
    private String accountId;
    private String accountType;
    private String counterpartyAccountId;
    private String counterpartyAccountType;
    private String counterpartyName;
    private String counterpartyWalletType;
    private String counterpartyCurrency;
    private String paymentReference;
    private String requestGateway;
    private String traceId;
    private String initiatedBy;
    private String remarks;
    private Map<String, Object> additionalInfo;
}
