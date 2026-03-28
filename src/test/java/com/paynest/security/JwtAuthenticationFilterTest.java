package com.paynest.security;

import com.paynest.config.security.JwtAuthenticationFilter;
import com.paynest.config.security.JwtService;
import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TenantContext;
import com.paynest.exception.ApiErrorResponseWriter;
import com.paynest.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
    private final ApiErrorResponseWriter apiErrorResponseWriter = mock(ApiErrorResponseWriter.class);

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService,
            userDetailsService,
            tenantRegistryService,
            apiErrorResponseWriter
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldAllowWhenTokenTenantClaimAlreadyContainsSchemaName() throws Exception {
        TenantContext.setTenant("tenant_movii");
        Claims claims = mock(Claims.class);
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/pay/U2U");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractAccountId("token")).thenReturn("acc-1");
        when(jwtService.extractTenant("token")).thenReturn("tenant_movii");
        when(jwtService.getClaims("token")).thenReturn(claims);
        when(userDetailsService.loadUserByUsername("acc-1"))
                .thenReturn(new User("acc-1", "", List.of()));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(apiErrorResponseWriter, never()).write(any(), any(), any(CommonErrorCode.class));
    }

    @Test
    void shouldRejectWhenTokenTenantDoesNotMatchRequestTenantSchema() throws Exception {
        TenantContext.setTenant("tenant_movii");
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/pay/U2U");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractAccountId("token")).thenReturn("acc-1");
        when(jwtService.extractTenant("token")).thenReturn("tenant-2");
        when(jwtService.getClaims("token")).thenReturn(Mockito.mock(Claims.class));
        when(tenantRegistryService.getSchema("tenant-2")).thenReturn("tenant_2_schema");

        filter.doFilter(request, response, filterChain);

        verify(apiErrorResponseWriter).write(eq(request), eq(response), eq(CommonErrorCode.INVALID_TOKEN));
        verify(filterChain, never()).doFilter(any(), any());
    }
}
