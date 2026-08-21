package com.paynest.limits.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpsertTransactionLimitProfileRequest {

    @NotBlank(message = "limitName is required")
    @Size(max = 150, message = "limitName must not exceed 150 characters")
    private String limitName;

    @NotNull(message = "tagId is required")
    private Long tagId;

    @NotBlank(message = "limitType is required")
    @Size(max = 20, message = "limitType must not exceed 20 characters")
    private String limitType;

    @NotBlank(message = "subjectKey is required")
    @Size(max = 50, message = "subjectKey must not exceed 50 characters")
    private String subjectKey;

    private JsonNode details;

    @Size(max = 20, message = "status must not exceed 20 characters")
    private String status;

    @NotBlank(message = "walletType is required")
    @Size(max = 50, message = "walletType must not exceed 50 characters")
    private String walletType;

    @NotBlank(message = "currency is required")
    @Size(max = 10, message = "currency must not exceed 10 characters")
    private String currency;

    private BigDecimal minResidualBalance;

    private BigDecimal maxBalance;

    @Valid
    private List<TransactionLimitDetailRequest> limitDetails;
}
