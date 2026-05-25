package com.paynest.payments.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class RecentRecipientId implements Serializable {
    private String accountId;
    private String recipientAccountId;
    private String serviceCode;
    private String currency;
    private String walletType;
}
