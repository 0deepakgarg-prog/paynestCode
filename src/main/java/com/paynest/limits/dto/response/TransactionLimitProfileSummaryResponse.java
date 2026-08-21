package com.paynest.limits.dto.response;

import com.paynest.limits.entity.TransactionLimitProfile;
import com.paynest.tag.entity.Tag;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionLimitProfileSummaryResponse {

    private Long limitId;
    private String limitName;
    private Long tagId;
    private String tagCode;
    private String tagName;
    private String limitType;
    private String subjectKey;
    private String status;
    private String walletType;
    private String currency;
    private BigDecimal minResidualBalance;
    private BigDecimal maxBalance;
    private LocalDateTime createdOn;
    private LocalDateTime modifiedOn;

    public TransactionLimitProfileSummaryResponse(TransactionLimitProfile profile, Tag tag) {
        this.limitId = profile.getLimitId();
        this.limitName = profile.getLimitName();
        this.tagId = profile.getTagId();
        this.tagCode = tag == null ? null : tag.getTagCode();
        this.tagName = tag == null ? null : tag.getTagName();
        this.limitType = profile.getLimitType();
        this.subjectKey = profile.getSubjectKey();
        this.status = profile.getStatus();
        this.walletType = profile.getWalletType();
        this.currency = profile.getCurrency();
        this.minResidualBalance = profile.getMinResidualBalance();
        this.maxBalance = profile.getMaxBalance();
        this.createdOn = profile.getCreatedOn();
        this.modifiedOn = profile.getModifiedOn();
    }
}
