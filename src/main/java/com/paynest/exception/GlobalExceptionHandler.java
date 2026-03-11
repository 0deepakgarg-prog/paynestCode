package com.paynest.exception;


import com.paynest.dto.ApiErrorResponse;
import com.paynest.enums.TransactionStatus;
import com.paynest.payment.dto.U2UPaymentResponse;
import com.paynest.tenant.TraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

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

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<U2UPaymentResponse> handleAppExceptionV2(
            ApplicationException ex) {

        U2UPaymentResponse response = U2UPaymentResponse.builder()
                .message(ex.getErrorMessage())
                .code(ex.getErrorCode())
                .responseStatus(TransactionStatus.FAILURE)
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId()).build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

}
