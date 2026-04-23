package com.paynest.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailResponse {

    private String transactionId;
    private String transferOn;
    private String accountId;
    private String serviceCode;
    private String serviceName;
    private String transferStatus;
    private String status;
    private String errorCode;
    private String entryType;
    private BigDecimal transactionAmount;
    private BigDecimal approvedAmount;
    private BigDecimal requestedAmount;
    private BigDecimal previousBalance;
    private BigDecimal postBalance;
    private BigDecimal previousFicBalance;
    private BigDecimal postFicBalance;
    private BigDecimal previousFrozenBalance;
    private BigDecimal postFrozenBalance;
    private String paymentReference;
    private String requestGateway;
    private String traceId;
    private String initiatedBy;
    private String remarks;
    private String reconciliationDone;
    private String reconciliationBy;
    private String reconciliationDate;
    private TransactionPartyDetailResponse debitor;
    private TransactionPartyDetailResponse creditor;
    private List<TransactionEntryDetailResponse> entries;
    private Map<String, Object> additionalInfo;
    private LocalDateTime responseTimestamp;
}
