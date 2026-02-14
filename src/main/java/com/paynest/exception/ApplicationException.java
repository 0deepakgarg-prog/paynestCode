package com.paynest.exception;

public class ApplicationException extends Throwable {
    public ApplicationException(String message) {
        try {
            throw new Exception(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
