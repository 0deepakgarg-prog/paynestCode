package com.paynest.users.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AccountStatusChangeResponse {

    private String accountId;
    private String accountType;
    private String previousStatus;
    private String newStatus;
    private String actionType;
    private String performedBy;
    private LocalDateTime performedAt;
}
