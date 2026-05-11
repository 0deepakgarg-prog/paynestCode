package com.paynest.users.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class WalletRestrictionResponse {

    private Long walletId;
    private JsonNode restrictions;
    private Long version;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
