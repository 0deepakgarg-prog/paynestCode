package com.paynest.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GetAccountDetailsRequest {

    @JsonProperty("tenantId")
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

    @JsonProperty("accountId")
    private String accountId;
}

