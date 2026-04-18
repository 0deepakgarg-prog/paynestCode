package com.paynest.statements.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDocument {

    private String transactionId;
    private String transferOn;
    private String serviceCode;
    private String serviceName;
    private String status;
    private String transferStatus;
    private String entryType;
    private String transactionDirection;
    private BigDecimal transactionAmount;
    private BigDecimal serviceChargePaid;
    private BigDecimal totalAmountPaid;
    private String totalAmountLabel;
    private BigDecimal approvedAmount;
    private BigDecimal requestedAmount;
    private BigDecimal previousBalance;
    private BigDecimal postBalance;
    private String currency;
    private String accountId;
    private String accountMobileNumber;
    private String preferredLanguage;
    private String paymentReference;
    private String traceId;
    private String initiatedBy;
    private String remarks;
    private ReceiptParty debtor;
    private ReceiptParty creditor;
    private Map<String, Object> additionalInfo;
    private String language;
    private String templateVersion;
    private String generatedAt;
    private String currentYear;
}
