package com.paynest.exception;

import org.springframework.http.HttpStatus;

public interface CatalogErrorCode {
    String code();
    HttpStatus httpStatus();
}
