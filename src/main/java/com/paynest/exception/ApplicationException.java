package com.paynest.exception;

public class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public ApplicationException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
