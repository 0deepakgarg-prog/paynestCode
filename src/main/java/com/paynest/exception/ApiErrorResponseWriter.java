package com.paynest.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.dto.response.ApiErrorResponse;
import com.paynest.enums.TransactionStatus;
import com.paynest.payment.dto.U2UPaymentResponse;
import com.paynest.service.ErrorCatalogService;
import com.paynest.service.TenantRegistryService;
import com.paynest.tenant.TenantContext;
import com.paynest.tenant.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiErrorResponseWriter {

    private final ErrorCatalogService errorCatalogService;
    private final TenantRegistryService tenantRegistryService;
    private final ObjectMapper objectMapper;

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            CatalogErrorCode errorCode
    ) throws IOException {
        write(request, response, errorCode, Map.of(), null);
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            CatalogErrorCode errorCode,
            Map<String, Object> params,
            String transactionId
    ) throws IOException {
        boolean tenantContextCreated = ensureTenantContextFromHeader(request);
        try {
            ErrorCatalogService.ResolvedError resolvedError = errorCatalogService.resolve(
                    errorCode.code(),
                    params,
                    null,
                    errorCode.httpStatus()
            );

            response.resetBuffer();
            response.setStatus(resolvedError.httpStatus().value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    buildResponseBody(request, errorCode.code(), resolvedError.message(), transactionId)
            );
            response.flushBuffer();
        } finally {
            if (tenantContextCreated) {
                TenantContext.clear();
            }
        }
    }

    private boolean ensureTenantContextFromHeader(HttpServletRequest request) {
        if (TenantContext.getTenant() != null || request == null) {
            return false;
        }

        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }

        try {
            String schema = tenantRegistryService.getSchema(tenantId);
            if (schema == null || schema.isBlank()) {
                return false;
            }

            TenantContext.setTenant(schema);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object buildResponseBody(
            HttpServletRequest request,
            String code,
            String message,
            String transactionId
    ) {
        if (isPaymentRequest(request)) {
            return U2UPaymentResponse.builder()
                    .responseStatus(TransactionStatus.FAILURE)
                    .operationType(extractOperationType(request))
                    .code(code)
                    .message(message)
                    .timestamp(Instant.now())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transactionId)
                    .build();
        }

        return new ApiErrorResponse(
                TransactionStatus.FAILURE,
                code,
                message,
                TraceContext.getTraceId(),
                LocalDateTime.now()
        );
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
}
