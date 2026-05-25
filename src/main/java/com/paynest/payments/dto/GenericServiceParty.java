package com.paynest.payments.dto;

import lombok.Data;

@Data
public class GenericServiceParty {

    private String accountId;

    private String accountCode;

    private String accountType;

    private String walletType;

    private String currency;
}
