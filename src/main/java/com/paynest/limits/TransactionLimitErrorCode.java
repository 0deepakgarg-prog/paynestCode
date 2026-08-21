package com.paynest.limits;

import com.paynest.exception.CatalogErrorCode;
import org.springframework.http.HttpStatus;

public enum TransactionLimitErrorCode implements CatalogErrorCode {
    LIMIT_TAG_NOT_FOUND(HttpStatus.BAD_REQUEST),
    LIMIT_SUBJECT_KEY_MISSING(HttpStatus.BAD_REQUEST),
    LIMIT_SUBJECT_VALUE_NOT_FOUND(HttpStatus.BAD_REQUEST),
    LIMIT_PROFILE_NOT_FOUND(HttpStatus.BAD_REQUEST),
    LIMIT_PROFILE_DETAILS_NOT_FOUND(HttpStatus.BAD_REQUEST),
    LIMIT_PERIOD_NOT_CONFIGURED(HttpStatus.BAD_REQUEST),
    LIMIT_MIN_TRANSACTION_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST),
    LIMIT_MAX_TRANSACTION_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST),
    LIMIT_DAILY_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST),
    LIMIT_DAILY_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST),
    LIMIT_MONTHLY_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST),
    LIMIT_MONTHLY_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST),
    LIMIT_MIN_RESIDUAL_BALANCE_NOT_MET(HttpStatus.BAD_REQUEST),
    LIMIT_MAX_BALANCE_EXCEEDED(HttpStatus.BAD_REQUEST);

    private final HttpStatus httpStatus;

    TransactionLimitErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
