package com.paynest.config;

import com.paynest.dto.logging.ApiAuditLogEvent;
import com.paynest.service.AsyncLogPublisher;
import com.paynest.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class ApiAuditKafkaFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LENGTH = 4000;
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final AsyncLogPublisher asyncLogPublisher;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String correlationId = resolveCorrelationId(request);
        wrappedResponse.setHeader(CORRELATION_HEADER, correlationId);
        LocalDateTime requestTime = LocalDateTime.now();
        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            String requestBody = redactSecrets(truncate(readBody(wrappedRequest.getContentAsByteArray(), wrappedRequest.getCharacterEncoding())));
            String responseBody = redactSecrets(truncate(readBody(wrappedResponse.getContentAsByteArray(), wrappedResponse.getCharacterEncoding())));
            long durationMs = System.currentTimeMillis() - start;

            ApiAuditLogEvent requestEvent = ApiAuditLogEvent.builder()
                    .eventType("REQUEST_RECEIVED")
                    .correlationId(correlationId)
                    .eventTime(requestTime)
                    .method(request.getMethod())
                    .path(request.getRequestURI())
                    .query(request.getQueryString())
                    .tenantId(resolveTenant(request))
                    .accountId(resolveAccountId())
                    .clientIp(resolveClientIp(request))
                    .headers(extractHeaders(request))
                    .requestBody(requestBody)
                    .build();

            ApiAuditLogEvent responseEvent = ApiAuditLogEvent.builder()
                    .eventType("RESPONSE_SENT")
                    .correlationId(correlationId)
                    .eventTime(LocalDateTime.now())
                    .method(request.getMethod())
                    .path(request.getRequestURI())
                    .query(request.getQueryString())
                    .status(wrappedResponse.getStatus())
                    .durationMs(durationMs)
                    .tenantId(resolveTenant(request))
                    .accountId(resolveAccountId())
                    .clientIp(resolveClientIp(request))
                    .responseBody(responseBody)
                    .build();

            asyncLogPublisher.publish(requestEvent);
            asyncLogPublisher.publish(responseEvent);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private String resolveTenant(HttpServletRequest request) {
        String tenant = TenantContext.getTenant();
        if (tenant != null && !tenant.isBlank()) {
            return tenant;
        }
        return request.getHeader(TENANT_HEADER);
    }

    private String resolveAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();

        while (names != null && names.hasMoreElements()) {
            String header = names.nextElement();
            String value = request.getHeader(header);
            if ("authorization".equalsIgnoreCase(header) && value != null) {
                headers.put(header, "****");
            } else {
                headers.put(header, value);
            }
        }
        return headers;
    }

    private String readBody(byte[] body, String encoding) {
        if (body == null || body.length == 0) {
            return null;
        }
        Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        return new String(body, charset);
    }

    private String truncate(String payload) {
        if (payload == null) {
            return null;
        }
        if (payload.length() <= MAX_PAYLOAD_LENGTH) {
            return payload;
        }
        return payload.substring(0, MAX_PAYLOAD_LENGTH) + "...[TRUNCATED]";
    }

    private String redactSecrets(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        return payload
                .replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"****\"")
                .replaceAll("(?i)\"pin\"\\s*:\\s*\"[^\"]*\"", "\"pin\":\"****\"")
                .replaceAll("(?i)\"credential\"\\s*:\\s*\"[^\"]*\"", "\"credential\":\"****\"")
                .replaceAll("(?i)\"authValue\"\\s*:\\s*\"[^\"]*\"", "\"authValue\":\"****\"");
    }
}
