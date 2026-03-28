package com.paynest.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

@Getter
public class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final String transactionId;
    private final Map<String, Object> params;
    private final HttpStatus httpStatus;

    public ApplicationException(String errorCode, String errorMessage) {
        this(errorCode, errorMessage, null, Collections.emptyMap(), HttpStatus.BAD_REQUEST);
    }

    public ApplicationException(String errorCode, String errorMessage, String transactionId) {
        this(errorCode, errorMessage, transactionId, Collections.emptyMap(), HttpStatus.BAD_REQUEST);
    }

    public ApplicationException(String errorCode, String errorMessage, Map<String, Object> params) {
        this(errorCode, errorMessage, null, params, HttpStatus.BAD_REQUEST);
    }

    public ApplicationException(String errorCode, String errorMessage, String transactionId, Map<String, Object> params) {
        this(errorCode, errorMessage, transactionId, params, HttpStatus.BAD_REQUEST);
    }

    public ApplicationException(String errorCode, String errorMessage, HttpStatus httpStatus) {
        this(errorCode, errorMessage, null, Collections.emptyMap(), httpStatus);
    }

    public ApplicationException(String errorCode, String errorMessage, String transactionId, Map<String, Object> params, HttpStatus httpStatus) {
        super(errorMessage == null || errorMessage.isBlank() ? errorCode : errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.transactionId = transactionId;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(params);
        this.httpStatus = httpStatus == null ? HttpStatus.BAD_REQUEST : httpStatus;
    }

    public ApplicationException(CatalogErrorCode errorCode) {
        this(errorCode, null, null, Collections.emptyMap());
    }

    public ApplicationException(CatalogErrorCode errorCode, String transactionId) {
        this(errorCode, null, transactionId, Collections.emptyMap());
    }

    public ApplicationException(CatalogErrorCode errorCode, Map<String, Object> params) {
        this(errorCode, null, null, params);
    }

    public ApplicationException(CatalogErrorCode errorCode, String errorMessage, Map<String, Object> params) {
        this(errorCode, errorMessage, null, params);
    }

    public ApplicationException(CatalogErrorCode errorCode, String errorMessage, String transactionId, Map<String, Object> params) {
        this(
                errorCode.code(),
                errorMessage,
                transactionId,
                params,
                errorCode.httpStatus()
        );
    }

    public ApplicationException withTransactionId(String transactionId) {
        if (this.transactionId != null || transactionId == null || transactionId.isBlank()) {
            return this;
        }

        return new ApplicationException(errorCode, errorMessage, transactionId, params, httpStatus);
    }

}

