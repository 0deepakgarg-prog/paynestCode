package com.paynest.users.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class WalletRestrictionHistoryResponse {

    private Long historyId;
    private Long walletId;
    private Long version;
    private JsonNode restrictions;
    private String actionType;
    private String changedBy;
    private LocalDateTime createdAt;
}
