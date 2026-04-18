package com.paynest.statements.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptParty {

    private String accountId;
    private String accountType;
    private String accountName;
    private String mobileNumber;
    private Long walletId;
    private String walletType;
    private String currency;
}
