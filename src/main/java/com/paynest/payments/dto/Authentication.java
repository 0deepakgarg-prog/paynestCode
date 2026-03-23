package com.paynest.payments.dto;

import com.paynest.users.enums.AuthType;
import lombok.Data;

@Data
public class Authentication {
    private AuthType type;

    private String value;
}

