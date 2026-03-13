package com.paynest.payment.dto;

import com.paynest.enums.AccountType;
import lombok.Data;

@Data
public class Party {

    private AccountType accountType;

    private Identifier identifier;

    private Authentication authentication;
}
