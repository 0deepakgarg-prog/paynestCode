
package com.paynest.config;

import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TraceContext;
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
import com.paynest.exception.ApplicationException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRegistryService tenantService;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString();
        TraceContext.setTraceId(traceId);
        String tenant = TenantContext.getTenant();


        try {
            tenantService.ensureTenantsLoaded();

            if (tenant == null) {
                tenant = request.getHeader("X-Tenant-Id");
            }
            if (tenant == null) {
                log.error("Filter error. code=X_TENANT_ID_MISSING, message=X-Tenant-Id missing, traceId={}", traceId);
                FilterErrorResponseWriter.write(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "X_TENANT_ID_MISSING",
                        "X-Tenant-Id missing"
                );
                return;
            }

            String schema = tenantService.getSchema(tenant);
            if (schema == null || schema.isBlank()) {
                log.error("Filter error. code=UNKNOWN_TENANT, message=Unknown tenant, tenant={}, traceId={}", tenant, traceId);
                FilterErrorResponseWriter.write(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "UNKNOWN_TENANT",
                        "Unknown tenant"
                );
                return;
            }

            TenantContext.setTenant(schema);
            log.info("Tenant before filter chain: {} and request TraceId {}", TenantContext.getTenant(), traceId);
            filterChain.doFilter(request, response);
        } catch (ApplicationException ex) {
            log.error("Filter error. code={}, message={}, traceId={}", ex.getErrorCode(), ex.getErrorMessage(), traceId, ex);
            FilterErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    ex.getErrorCode(),
                    ex.getErrorMessage()
            );
        } catch (Exception ex) {
            log.error("Filter error. code=FILTER_ERROR, message={}, traceId={}", ex.getMessage(), traceId, ex);
            FilterErrorResponseWriter.write(
                    response,
                    FilterErrorResponseWriter.resolveHttpStatus(ex),
                    "FILTER_ERROR",
                    ex.getMessage() == null ? "Unexpected filter error" : ex.getMessage()
            );
        } finally {
            log.info("Clearing tenant context for tenant ID: {} and Trace Context {}", tenant,TraceContext.getTraceId());
            MDC.clear();
            TenantContext.clear();
            TraceContext.clear();
        }
    }
}

