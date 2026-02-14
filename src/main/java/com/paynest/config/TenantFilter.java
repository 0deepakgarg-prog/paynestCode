
package com.paynest.config;

import com.paynest.service.TenantRegistryService;
import com.paynest.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRegistryService tenantService;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String tenant = request.getHeader("X-Tenant-Id");
        if (tenant == null) {
            response.sendError(400, "X-Tenant-Id missing");
            return;
        }

        String schema = tenantService.getSchema(tenant);

        TenantContext.setTenant(schema);
        //MDC.put("tenantId", tenant);
        log.info("Tenant before filter chain: {}", TenantContext.getTenant());
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("Clearing tenant context for tenant ID: {}", tenant);
            MDC.clear();
            TenantContext.clear();
        }
    }
}
