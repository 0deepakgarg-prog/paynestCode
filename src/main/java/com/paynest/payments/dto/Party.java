package com.paynest.payments.dto;

import com.paynest.enums.AccountType;
import com.paynest.users.enums.WalletType;
import lombok.Data;

@Data
public class Party {

    private AccountType accountType;

    private Identifier identifier;

    private WalletType walletType;

    private Authentication authentication;
}
