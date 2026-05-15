package com.paynest.payments.qr.dto;

import com.paynest.enums.AccountType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QrCreditor {

    @NotNull
    private IdentifierType identifierType;

    @NotBlank
    private String identifierValue;

    @NotNull
    private AccountType accountType;

    @NotNull
    private WalletType walletType;
}
