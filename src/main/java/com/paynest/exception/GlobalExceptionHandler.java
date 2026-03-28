package com.paynest.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.paynest.config.dto.response.ApiErrorResponse;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.service.ErrorCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorCatalogService errorCatalogService;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ErrorDetails errorDetails = resolveReadableBodyError(ex);
        ErrorCatalogService.ResolvedError resolvedError = errorCatalogService.resolve(
                errorDetails.code().code(),
                errorDetails.params(),
                errorDetails.fallbackMessage(),
                errorDetails.code().httpStatus()
        );

        if (isPaymentRequest(request)) {
            return ResponseEntity.status(resolvedError.httpStatus())
                    .body(buildPaymentErrorResponse(
                            request,
                            errorDetails.code().code(),
                            resolvedError.message(),
                            null
                    ));
        }

        return ResponseEntity.status(resolvedError.httpStatus())
                .body(buildApiErrorResponse(
                        errorDetails.code().code(),
                        resolvedError.message()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex, HttpServletRequest request) {
        ErrorCatalogService.ResolvedError resolvedError = errorCatalogService.resolve(
                CommonErrorCode.INTERNAL_ERROR.code(),
                Map.of(),
                null,
                CommonErrorCode.INTERNAL_ERROR.httpStatus()
        );

        if (isPaymentRequest(request)) {
            return ResponseEntity.status(resolvedError.httpStatus())
                    .body(buildPaymentErrorResponse(
                            request,
                            CommonErrorCode.INTERNAL_ERROR.code(),
                            resolvedError.message(),
                            null
                    ));
        }

        return ResponseEntity.status(resolvedError.httpStatus())
                .body(buildApiErrorResponse(
                        CommonErrorCode.INTERNAL_ERROR.code(),
                        resolvedError.message()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();
        ErrorCatalogService.ResolvedError resolvedError = errorCatalogService.resolve(
                CommonErrorCode.VALIDATION_ERROR.code(),
                Map.of("detail", errorMessage),
                errorMessage,
                CommonErrorCode.VALIDATION_ERROR.httpStatus()
        );

        if (isPaymentRequest(request)) {
            return ResponseEntity.status(resolvedError.httpStatus())
                    .body(buildPaymentErrorResponse(
                            request,
                            CommonErrorCode.VALIDATION_ERROR.code(),
                            resolvedError.message(),
                            null
                    ));
        }

        return ResponseEntity.status(resolvedError.httpStatus())
                .body(buildApiErrorResponse(
                        CommonErrorCode.VALIDATION_ERROR.code(),
                        resolvedError.message()
                ));
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<?> handleApplicationException(
            ApplicationException ex,
            HttpServletRequest request) {
        ErrorCatalogService.ResolvedError resolvedError = errorCatalogService.resolve(
                ex.getErrorCode(),
                ex.getParams(),
                ex.getErrorMessage(),
                ex.getHttpStatus()
        );

        if (isPaymentRequest(request)) {
            return ResponseEntity.status(resolvedError.httpStatus())
                    .body(buildPaymentErrorResponse(
                            request,
                            ex.getErrorCode(),
                            resolvedError.message(),
                            ex.getTransactionId()
                    ));
        }

        return ResponseEntity.status(resolvedError.httpStatus())
                .body(buildApiErrorResponse(
                        ex.getErrorCode(),
                        resolvedError.message()
                ));
    }

    private ApiErrorResponse buildApiErrorResponse(String code, String message) {
        return new ApiErrorResponse(
                TransactionStatus.FAILURE,
                code,
                message,
                TraceContext.getTraceId(),
                LocalDateTime.now()
        );
    }

    private BasePaymentResponse buildPaymentErrorResponse(
            HttpServletRequest request,
            String code,
            String message,
            String transactionId) {
        return BasePaymentResponse.builder()
                .operationType(extractOperationType(request))
                .message(message)
                .code(code)
                .responseStatus(TransactionStatus.FAILURE)
                .timestamp(Instant.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .build();
    }

    private ErrorDetails resolveReadableBodyError(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            return resolveInvalidFormatError(invalidFormatException);
        }

        return new ErrorDetails(
                CommonErrorCode.INVALID_REQUEST_BODY,
                null,
                Map.of()
        );
    }

    private ErrorDetails resolveInvalidFormatError(InvalidFormatException ex) {
        String fieldPath = buildFieldPath(ex);
        String invalidValue = String.valueOf(ex.getValue());

        if (ex.getTargetType() != null && ex.getTargetType().isEnum()) {
            String allowedValues = Arrays.stream(ex.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

            return new ErrorDetails(
                    CommonErrorCode.INVALID_ENUM_VALUE,
                    null,
                    Map.of(
                            "value", invalidValue,
                            "field", fieldPath,
                            "allowedValues", allowedValues
                    )
            );
        }

        return new ErrorDetails(
                CommonErrorCode.INVALID_REQUEST_BODY,
                null,
                Map.of(
                        "value", invalidValue,
                        "field", fieldPath
                )
        );
    }

    private String buildFieldPath(JsonMappingException ex) {
        if (ex.getPath() == null || ex.getPath().isEmpty()) {
            return "requestBody";
        }

        return ex.getPath().stream()
                .map(reference -> {
                    if (reference.getFieldName() != null) {
                        return reference.getFieldName();
                    }

                    return reference.getIndex() >= 0
                            ? "[" + reference.getIndex() + "]"
                            : "unknown";
                })
                .collect(Collectors.joining("."));
    }

    private boolean isPaymentRequest(HttpServletRequest request) {
        return request != null
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith("/api/v1/pay/");
    }

    private String extractOperationType(HttpServletRequest request) {
        if (!isPaymentRequest(request)) {
            return null;
        }

        String uri = request.getRequestURI();
        int lastSlashIndex = uri.lastIndexOf('/');
        return lastSlashIndex >= 0 ? uri.substring(lastSlashIndex + 1) : null;
    }

    private record ErrorDetails(
            CatalogErrorCode code,
            String fallbackMessage,
            Map<String, Object> params
    ) {
    }
}
