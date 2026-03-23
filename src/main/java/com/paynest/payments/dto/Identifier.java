package com.paynest.payments.dto;

import com.paynest.users.enums.IdentifierType;
import lombok.Data;

@Data
public class Identifier {
    private IdentifierType type;

    private String value;
}

