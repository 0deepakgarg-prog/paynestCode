package com.paynest.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntryDetailResponse {

    private Long sequenceNumber;
    private String accountId;
    private String userType;
    private String entryType;
    private String identifierId;
    private String secondIdentifierId;
    private Long walletId;
    private String transferOn;
    private String serviceCode;
    private String transferStatus;
    private BigDecimal transactionAmount;
    private BigDecimal approvedAmount;
    private BigDecimal previousBalance;
    private BigDecimal postBalance;
    private BigDecimal previousFicBalance;
    private BigDecimal postFicBalance;
    private BigDecimal previousFrozenBalance;
    private BigDecimal postFrozenBalance;
}
