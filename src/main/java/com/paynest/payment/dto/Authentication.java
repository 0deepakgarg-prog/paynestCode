package com.paynest.payment.dto;

import com.paynest.enums.AuthType;
import lombok.Data;

@Data
public class Authentication {
    private AuthType type;

    private String value;
}
