package com.paynest.config.security;

import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.FilterErrorResponseWriter;
import com.paynest.exception.ApplicationException;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        log.info("Inside JwtAuthenticationFilter");
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);
            if (!jwtService.isTokenValid(token)) {
                log.error("Filter error. code=INVALID_TOKEN, message=Invalid Token");
                FilterErrorResponseWriter.write(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID_TOKEN",
                        "Invalid Token"
                );
                return;
            }

            String accountId = jwtService.extractAccountId(token);
            String tenantId = jwtService.extractTenant(token);
            Claims claims = jwtService.getClaims(token);
            log.info("tenant is :" + tenantId);

            if (accountId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                TenantContext.setTenant(tenantRegistryService.getSchema(tenantId));
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
        } catch (ApplicationException ex) {
            log.error("Filter error. code={}, message={}", ex.getErrorCode(), ex.getErrorMessage(), ex);
            FilterErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    ex.getErrorCode(),
                    ex.getErrorMessage()
            );
        } catch (Exception ex) {
            log.error("Filter error. code=JWT_FILTER_ERROR, message={}", ex.getMessage(), ex);
            FilterErrorResponseWriter.write(
                    response,
                    FilterErrorResponseWriter.resolveHttpStatus(ex),
                    "JWT_FILTER_ERROR",
                    ex.getMessage() == null ? "Unexpected JWT filter error" : ex.getMessage()
            );
        }
    }
}

