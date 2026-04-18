package com.paynest.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.config.dto.response.ApiErrorResponse;
import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TenantTime;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.service.ErrorCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
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
            TenantContext.setTenantId(tenantId);
            TenantContext.setTimeZone(tenantRegistryService.getTimeZone(tenantId));
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
            return BasePaymentResponse.builder()
                    .responseStatus(TransactionStatus.FAILURE)
                    .operationType(extractOperationType(request))
                    .code(code)
                    .message(message)
                    .timestamp(TenantTime.instant())
                    .traceId(TraceContext.getTraceId())
                    .transactionId(transactionId)
                    .build();
        }

        return new ApiErrorResponse(
                TransactionStatus.FAILURE,
                code,
                message,
                TraceContext.getTraceId(),
                TenantTime.now()
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
        String prefix = "/api/v1/pay/";
        if (!uri.startsWith(prefix) || uri.length() <= prefix.length()) {
            return null;
        }

        String operationType = uri.substring(prefix.length());
        if (operationType.endsWith("/")) {
            operationType = operationType.substring(0, operationType.length() - 1);
        }
        if (operationType.isBlank()) {
            return null;
        }
        if (!operationType.contains("/")) {
            return operationType;
        }

        return operationType.replace('/', '_').toUpperCase(Locale.ROOT);
    }
}
