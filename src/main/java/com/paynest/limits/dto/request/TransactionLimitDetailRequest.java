package com.paynest.limits.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TransactionLimitDetailRequest {

    private Long limitDetailsId;

    @Size(max = 20, message = "partyType must not exceed 20 characters")
    private String partyType;

    @Size(max = 20, message = "status must not exceed 20 characters")
    private String status;

    @Size(max = 50, message = "operationType must not exceed 50 characters")
    private String operationType;

    @Size(max = 50, message = "requestGateway must not exceed 50 characters")
    private String requestGateway;

    private BigDecimal minTxnAmount;

    private BigDecimal maxTxnAmount;

    @Valid
    private List<TransactionLimitPeriodRequest> periods;
}
