package com.paynest.users.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WalletRestrictionRequest {

    private Long walletId;

    @NotNull
    private JsonNode restrictions;
}
