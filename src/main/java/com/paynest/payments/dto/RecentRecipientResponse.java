package com.paynest.payments.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecentRecipientResponse {
    private String accountId;
    private String recipientAccountId;
    private String recipientAccountType;
    private String recipientIdentifierType;
    private String recipientIdentifierValue;
    private String recipientDisplayName;
    private String serviceCode;
    private String currency;
    private String walletType;
    private String lastTransactionId;
    private LocalDateTime lastPaidAt;
    private Long paymentCount;
    private String field1;
    private String field2;
    private String field3;
    private String field4;
    private String field5;
}
