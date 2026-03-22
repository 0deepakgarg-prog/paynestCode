package com.paynest.security;

import com.paynest.exception.ApiErrorResponseWriter;
import com.paynest.exception.CommonErrorCode;
import com.paynest.service.TenantRegistryService;
import com.paynest.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TenantRegistryService tenantRegistryService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        log.info("Inside JwtAuthenticationFilter");

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            apiErrorResponseWriter.write(request, response, CommonErrorCode.INVALID_TOKEN);
            return;
        }

        String accountId = jwtService.extractAccountId(token);
        String tenantClaim = jwtService.extractTenant(token);
        Claims claims = jwtService.getClaims(token);
        String requestTenantSchema = TenantContext.getTenant();
        String resolvedTokenTenantSchema = resolveTokenTenantSchema(tenantClaim, requestTenantSchema);

        if (tenantClaim == null || tenantClaim.isBlank()
                || resolvedTokenTenantSchema == null || resolvedTokenTenantSchema.isBlank()
                || requestTenantSchema == null || requestTenantSchema.isBlank()
                || !requestTenantSchema.equals(resolvedTokenTenantSchema)) {
            apiErrorResponseWriter.write(request, response, CommonErrorCode.INVALID_TOKEN);
            return;
        }

        log.info("tenant is :" + tenantClaim);

        if (accountId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            TenantContext.setTenant(resolvedTokenTenantSchema);
            UserDetails userDetails = userDetailsService.loadUserByUsername(accountId);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
            authenticationToken.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveTokenTenantSchema(String tenantClaim, String requestTenantSchema) {
        if (tenantClaim == null || tenantClaim.isBlank()) {
            return null;
        }

        if (requestTenantSchema != null && requestTenantSchema.equals(tenantClaim)) {
            return tenantClaim;
        }

        return tenantRegistryService.getSchema(tenantClaim);
    }
}
