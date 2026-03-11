package com.paynest.payment.dto;

import com.paynest.enums.IdentifierType;
import lombok.Data;

@Data
public class Identifier {
    private IdentifierType type;

    private String value;
}
