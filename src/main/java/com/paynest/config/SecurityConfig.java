package com.paynest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.config.repository.AuditApiLogRepository;
import com.paynest.config.security.JwtAuthenticationFilter;
import com.paynest.exception.ApiErrorResponseWriter;
import com.paynest.exception.CommonErrorCode;
import com.paynest.config.service.AsyncLogPublisher;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantFilter tenantFilter;
    private final SecurityAuthenticationEntryPoint securityAuthenticationEntryPoint;
    private final SecurityAccessDeniedHandler securityAccessDeniedHandler;
    private final AsyncLogPublisher asyncLogPublisher;
    private final AuditApiLogRepository auditApiLogRepository;
    private final ObjectMapper objectMapper;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityAuthenticationEntryPoint)
                        .accessDeniedHandler(securityAccessDeniedHandler)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/account/register/**",
                                "/api/v1/account/pin/changeDefault",
                                "/api/v1/account/password/changeDefault",
                                "/api/v1/account/register/**",
                                "/api/v1/account/registerUser",
                                "/api/v1/pay/settleTxn"

                )
                .permitAll()
                .anyRequest()
                .authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) ->
                                apiErrorResponseWriter.write(request, response, CommonErrorCode.TOKEN_REQUIRED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                apiErrorResponseWriter.write(request, response, CommonErrorCode.ACCESS_DENIED))
                )
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, TenantFilter.class)
                .addFilterAfter(apiAuditKafkaFilter(), TenantFilter.class);

        return http.build();
    }

    @Bean
    public ApiAuditKafkaFilter apiAuditKafkaFilter() {
        return new ApiAuditKafkaFilter(asyncLogPublisher, auditApiLogRepository, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(tenantFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ApiAuditKafkaFilter> apiAuditKafkaFilterRegistration(ApiAuditKafkaFilter apiAuditKafkaFilter) {
        FilterRegistrationBean<ApiAuditKafkaFilter> registration = new FilterRegistrationBean<>(apiAuditKafkaFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

