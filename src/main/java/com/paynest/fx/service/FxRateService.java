package com.paynest.fx.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.fx.dto.request.CreateFxRateRequest;
import com.paynest.fx.dto.response.FxRateResponse;
import com.paynest.fx.entity.FxRate;
import com.paynest.fx.repository.FxRateRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FxRateService {

    private static final String DEFAULT_RATE_TYPE = "MID";
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String PIVOT_CURRENCY = "USD";

    private final FxRateRepository fxRateRepository;
    private final EntityManager entityManager;

    @Transactional
    public FxRateResponse addFxRate(CreateFxRateRequest request) {
        String targetCurrency = normalizeCurrency(request.getTargetCurrency());
        if (PIVOT_CURRENCY.equals(targetCurrency)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "USD is the pivot currency and should not be configured as a target currency"
            );
        }
        FxRate fxRate = new FxRate();
        fxRate.setTargetCurrency(targetCurrency);
        fxRate.setUsdRate(request.getUsdRate());
        fxRate.setRateType(normalizeRateType(request.getRateType()));
        fxRate.setProvider(request.getProvider().trim());
        fxRate.setValidFrom(request.getValidFrom());
        fxRate.setVersionNo(resolveNextVersionNo(targetCurrency));
        fxRate.setIsActive(true);
        fxRate.setCreatedBy(resolveCurrentAccountId());
        fxRate.setField1(normalizeOptional(request.getField1()));
        fxRate.setField2(normalizeOptional(request.getField2()));
        fxRate.setField3(normalizeOptional(request.getField3()));
        fxRate.setField4(normalizeOptional(request.getField4()));
        fxRate.setField5(normalizeOptional(request.getField5()));

        FxRate savedFxRate = fxRateRepository.saveAndFlush(fxRate);
        entityManager.refresh(savedFxRate);
        return new FxRateResponse(savedFxRate);
    }

    private String normalizeCurrency(String currency) {
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "targetCurrency must be a 3-character ISO currency code");
        }
        return normalized;
    }

    private String normalizeRateType(String rateType) {
        if (rateType == null || rateType.isBlank()) {
            return DEFAULT_RATE_TYPE;
        }
        return rateType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long resolveNextVersionNo(String targetCurrency) {
        Long lastVersionNo = fxRateRepository.findLastVersionNoByTargetCurrency(targetCurrency);
        return lastVersionNo == null ? 1L : lastVersionNo + 1L;
    }

    private String resolveCurrentAccountId() {
        try {
            String accountId = JWTUtils.getCurrentAccountId();
            return accountId == null || accountId.isBlank() ? SYSTEM_ACTOR : accountId;
        } catch (Exception ex) {
            return SYSTEM_ACTOR;
        }
    }
}
