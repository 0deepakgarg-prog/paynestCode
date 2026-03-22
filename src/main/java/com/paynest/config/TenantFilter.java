
package com.paynest.config;

import com.paynest.exception.ApiErrorResponseWriter;
import com.paynest.exception.CommonErrorCode;
import com.paynest.service.TenantRegistryService;
import com.paynest.tenant.RequestLanguageContext;
import com.paynest.tenant.TenantContext;
import com.paynest.tenant.TraceContext;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRegistryService tenantService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        tenantService.ensureTenantsLoaded();

        String tenant = TenantContext.getTenant();

        if(tenant == null) {
            tenant = request.getHeader("X-Tenant-Id");
        }
        if (tenant == null) {
            apiErrorResponseWriter.write(request, response, CommonErrorCode.TENANT_HEADER_MISSING);
            return;
        }

        String schema = tenantService.getSchema(tenant);
        if (schema == null || schema.isBlank()) {
            apiErrorResponseWriter.write(request, response, CommonErrorCode.UNKNOWN_TENANT);
            return;
        }

        TenantContext.setTenant( schema);
        //MDC.put("tenantId", tenant);
        String traceId = UUID.randomUUID().toString();
        TraceContext.setTraceId(traceId);
        log.info("Tenant before filter chain: {} and request TraceId {}", TenantContext.getTenant(),traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("Clearing tenant context for tenant ID: {} and Trace Context {}", tenant,TraceContext.getTraceId());
            MDC.clear();
            RequestLanguageContext.clear();
            TenantContext.clear();
            TraceContext.clear();
        }
    }
}
