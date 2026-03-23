package com.paynest.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public ApplicationException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}

