package com.paynest.config;

import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApiErrorResponseWriter;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.CommonErrorCode;
import com.paynest.tenant.RequestLanguageContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.paynest.config.tenant.TenantContext.clear;
import static com.paynest.config.tenant.TenantContext.getTenant;
import static com.paynest.config.tenant.TenantContext.setTenant;
import static com.paynest.config.tenant.TenantContext.setTenantId;
import static com.paynest.config.tenant.TenantContext.setTimeZone;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private static final String[] EXCLUDED_PATH_PREFIXES = {
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars"
    };

    private final TenantRegistryService tenantService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return false;
        }

        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString();
        TraceContext.setTraceId(traceId);
        String tenant = getTenant();


        try {
            tenantService.ensureTenantsLoaded();

            if (tenant == null || tenant.isBlank()) {
                tenant = request.getHeader("X-Tenant-Id");
            }
            if (tenant == null || tenant.isBlank()) {
                log.error("Filter error. code={}, traceId={}", CommonErrorCode.TENANT_HEADER_MISSING.code(), traceId);
                apiErrorResponseWriter.write(request, response, CommonErrorCode.TENANT_HEADER_MISSING);
                return;
            }

            String schema = tenantService.getSchema(tenant);
            if (schema == null || schema.isBlank()) {
                log.error(
                        "Filter error. code={}, tenant={}, traceId={}",
                        CommonErrorCode.UNKNOWN_TENANT.code(),
                        tenant,
                        traceId
                );
                apiErrorResponseWriter.write(request, response, CommonErrorCode.UNKNOWN_TENANT);
                return;
            }

            setTenant(schema);
            setTenantId(tenant);
            setTimeZone(tenantService.getTimeZone(tenant));
            log.info("Tenant before filter chain: {} and request TraceId {}", getTenant(), traceId);
            filterChain.doFilter(request, response);
        } catch (ApplicationException ex) {
            log.error("Filter error. code={}, message={}, traceId={}", ex.getErrorCode(), ex.getErrorMessage(), traceId, ex);
            FilterErrorResponseWriter.write(
                    response,
                    ex.getHttpStatus().value(),
                    ex.getErrorCode(),
                    ex.getErrorMessage() == null || ex.getErrorMessage().isBlank()
                            ? ex.getErrorCode()
                            : ex.getErrorMessage()
            );
        } catch (Exception ex) {
            log.error("Filter error. code=FILTER_ERROR, message={}, traceId={}", ex.getMessage(), traceId, ex);
            FilterErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "FILTER_ERROR",
                    ex.getMessage() == null ? "Unexpected filter error" : ex.getMessage()
            );
        } finally {
            log.info("Clearing tenant context for tenant ID: {} and Trace Context {}", tenant, TraceContext.getTraceId());
            MDC.clear();
            RequestLanguageContext.clear();
            clear();
            TraceContext.clear();
        }
    }
}
