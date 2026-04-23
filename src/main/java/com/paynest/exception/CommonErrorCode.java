package com.paynest.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements CatalogErrorCode {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST),
    INVALID_ENUM_VALUE(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    DEFAULT_LANGUAGE_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR),
    TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    TENANT_HEADER_MISSING(HttpStatus.BAD_REQUEST),
    UNKNOWN_TENANT(HttpStatus.NOT_FOUND);

    private final HttpStatus httpStatus;

    CommonErrorCode(HttpStatus httpStatus) {
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
