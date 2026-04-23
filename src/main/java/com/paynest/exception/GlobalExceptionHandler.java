package com.paynest.exception;


import com.paynest.config.dto.response.ApiErrorResponse;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.config.tenant.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*@ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(ApplicationException ex) {

        ApiErrorResponse response = new ApiErrorResponse(
                false,
                ex.getErrorCode(),
                ex.getErrorMessage(),
                LocalDateTime.now()
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }*/

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {

        ApiErrorResponse response = new ApiErrorResponse(
                TransactionStatus.FAILURE,
                "INTERNAL_ERROR",
                "Something went wrong",
                TraceContext.getTraceId(),
                LocalDateTime.now()
        );

        log.error("Global Exception", ex); // full stacktrace

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        ApiErrorResponse response =
                new ApiErrorResponse(
                        TransactionStatus.FAILURE,
                        "VALIDATION_ERROR",
                        errorMessage,
                        TraceContext.getTraceId(),
                        LocalDateTime.now()
                );
        log.error("Global Exception", ex); // full stacktrace

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<BasePaymentResponse> handleAppExceptionV2(
            ApplicationException ex) {

        BasePaymentResponse response = BasePaymentResponse.builder()
                .message(ex.getErrorMessage())
                .code(ex.getErrorCode())
                .responseStatus(TransactionStatus.FAILURE)
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId()).build();

        log.error("Global Exception", ex); // full stacktrace

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

}

