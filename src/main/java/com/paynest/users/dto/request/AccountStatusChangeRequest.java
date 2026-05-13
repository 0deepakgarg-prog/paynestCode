package com.paynest.users.dto.request;

import lombok.Data;

@Data
public class AccountStatusChangeRequest {

    private String reason;
    private String remarks;
}
