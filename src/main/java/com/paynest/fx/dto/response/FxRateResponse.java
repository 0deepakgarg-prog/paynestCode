package com.paynest.fx.dto.response;

import com.paynest.fx.entity.FxRate;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FxRateResponse {

    private Long rateId;
    private String targetCurrency;
    private BigDecimal usdRate;
    private String rateType;
    private String provider;
    private LocalDateTime validFrom;
    private Long versionNo;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private String createdBy;
    private String field1;
    private String field2;
    private String field3;
    private String field4;
    private String field5;

    public FxRateResponse(FxRate fxRate) {
        this.rateId = fxRate.getRateId();
        this.targetCurrency = fxRate.getTargetCurrency();
        this.usdRate = fxRate.getUsdRate();
        this.rateType = fxRate.getRateType();
        this.provider = fxRate.getProvider();
        this.validFrom = fxRate.getValidFrom();
        this.versionNo = fxRate.getVersionNo();
        this.isActive = fxRate.getIsActive();
        this.createdAt = fxRate.getCreatedAt();
        this.createdBy = fxRate.getCreatedBy();
        this.field1 = fxRate.getField1();
        this.field2 = fxRate.getField2();
        this.field3 = fxRate.getField3();
        this.field4 = fxRate.getField4();
        this.field5 = fxRate.getField5();
    }
}
