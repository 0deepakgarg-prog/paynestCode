package com.paynest.payments.dto;

import com.paynest.users.enums.AccountType;
import lombok.Data;

@Data
public class Party {

    private AccountType accountType;

    private Identifier identifier;

    private Authentication authentication;
}

