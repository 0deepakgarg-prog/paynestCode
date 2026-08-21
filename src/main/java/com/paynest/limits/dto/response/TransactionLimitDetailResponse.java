package com.paynest.limits.dto.response;

import com.paynest.limits.entity.TransactionLimitProfileDetail;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransactionLimitDetailResponse {

    private Long limitDetailsId;
    private Long limitId;
    private String partyType;
    private String status;
    private String operationType;
    private String requestGateway;
    private BigDecimal minTxnAmount;
    private BigDecimal maxTxnAmount;
    private LocalDateTime createdOn;
    private LocalDateTime modifiedOn;
    private List<TransactionLimitPeriodResponse> periods;

    public TransactionLimitDetailResponse(
            TransactionLimitProfileDetail detail,
            List<TransactionLimitPeriodResponse> periods
    ) {
        this.limitDetailsId = detail.getLimitDetailsId();
        this.limitId = detail.getLimitId();
        this.partyType = detail.getPartyType();
        this.status = detail.getStatus();
        this.operationType = detail.getOperationType();
        this.requestGateway = detail.getRequestGateway();
        this.minTxnAmount = detail.getMinTxnAmount();
        this.maxTxnAmount = detail.getMaxTxnAmount();
        this.createdOn = detail.getCreatedOn();
        this.modifiedOn = detail.getModifiedOn();
        this.periods = periods;
    }
}
