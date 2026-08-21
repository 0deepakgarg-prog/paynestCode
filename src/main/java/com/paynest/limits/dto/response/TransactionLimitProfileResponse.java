package com.paynest.limits.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.limits.entity.TransactionLimitProfile;
import com.paynest.tag.entity.Tag;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransactionLimitProfileResponse {

    private Long limitId;
    private String limitName;
    private Long tagId;
    private String tagCode;
    private String tagName;
    private String limitType;
    private String subjectKey;
    private JsonNode details;
    private String status;
    private String walletType;
    private String currency;
    private BigDecimal minResidualBalance;
    private BigDecimal maxBalance;
    private String createdBy;
    private LocalDateTime createdOn;
    private String modifiedBy;
    private LocalDateTime modifiedOn;
    private List<TransactionLimitDetailResponse> limitDetails;

    public TransactionLimitProfileResponse(
            TransactionLimitProfile profile,
            Tag tag,
            List<TransactionLimitDetailResponse> limitDetails
    ) {
        this.limitId = profile.getLimitId();
        this.limitName = profile.getLimitName();
        this.tagId = profile.getTagId();
        this.tagCode = tag == null ? null : tag.getTagCode();
        this.tagName = tag == null ? null : tag.getTagName();
        this.limitType = profile.getLimitType();
        this.subjectKey = profile.getSubjectKey();
        this.details = profile.getDetails();
        this.status = profile.getStatus();
        this.walletType = profile.getWalletType();
        this.currency = profile.getCurrency();
        this.minResidualBalance = profile.getMinResidualBalance();
        this.maxBalance = profile.getMaxBalance();
        this.createdBy = profile.getCreatedBy();
        this.createdOn = profile.getCreatedOn();
        this.modifiedBy = profile.getModifiedBy();
        this.modifiedOn = profile.getModifiedOn();
        this.limitDetails = limitDetails;
    }
}
