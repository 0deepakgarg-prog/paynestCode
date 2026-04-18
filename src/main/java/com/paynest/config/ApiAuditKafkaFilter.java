package com.paynest.config;


import com.paynest.config.tenant.TenantTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.config.dto.logging.ApiAuditLogEvent;
import com.paynest.config.entity.AuditApiLog;
import com.paynest.config.repository.AuditApiLogRepository;
import com.paynest.config.service.AsyncLogPublisher;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ApiAuditKafkaFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LENGTH = 4000;
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final AsyncLogPublisher asyncLogPublisher;
    private final AuditApiLogRepository auditApiLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null
                || !path.startsWith("/api/")
                || path.startsWith("/api/v1/download/receipt");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String correlationId = resolveCorrelationId(request);
        wrappedResponse.setHeader(CORRELATION_HEADER, correlationId);
        LocalDateTime requestTime = TenantTime.now();
        long start = TenantTime.epochMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            String requestBody = redactSecrets(truncate(readBody(wrappedRequest.getContentAsByteArray(), wrappedRequest.getCharacterEncoding())));
            String responseBody = redactSecrets(truncate(readResponseBody(wrappedResponse)));
            long durationMs = TenantTime.epochMillis() - start;

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
                    .eventTime(TenantTime.now())
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

            // asyncLogPublisher.publish(requestEvent);
            // asyncLogPublisher.publish(responseEvent);
            try {
                auditApiLogRepository.save(buildAuditApiLog(request, wrappedResponse, responseBody,
                        requestBody, durationMs));
            } catch (Exception ex) {
                log.warn(
                        "Unable to persist API audit log. method={}, path={}, status={}, traceId={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        wrappedResponse.getStatus(),
                        TraceContext.getTraceId(),
                        ex
                );
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    private AuditApiLog buildAuditApiLog(HttpServletRequest request,
                                         ContentCachingResponseWrapper response,
                                         String responseBody,String requestBody,
                                         long durationMs) {
        AuditApiLog auditApiLog = new AuditApiLog();
        auditApiLog.setTraceId(TraceContext.getTraceId());
        auditApiLog.setTenantId(resolveAuditTenantId(request));
        auditApiLog.setHttpMethod(request.getMethod());
        auditApiLog.setRequestBody(toJsonNode(requestBody));
        auditApiLog.setResponseBody(toJsonNode(responseBody));
        auditApiLog.setHttpStatus(response.getStatus());
        auditApiLog.setProcessingTimeMs(durationMs);
        return auditApiLog;
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

    private String resolveAuditTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return TenantContext.getTenant();
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

    private Map<String, Object> extractResponseHeaders(HttpServletResponse response) {
        Map<String, Object> headers = new HashMap<>();
        for (String headerName : response.getHeaderNames()) {
            var values = response.getHeaders(headerName);
            if (values == null || values.isEmpty()) {
                headers.put(headerName, null);
            } else if (values.size() == 1) {
                headers.put(headerName, values.iterator().next());
            } else {
                headers.put(headerName, values);
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

    private String readResponseBody(ContentCachingResponseWrapper response) {
        byte[] body = response.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return null;
        }

        String contentType = response.getContentType();
        if (!isTextContentType(contentType)) {
            return "[BINARY_RESPONSE contentType=%s length=%d]".formatted(contentType, body.length);
        }

        return readBody(body, response.getCharacterEncoding());
    }

    private boolean isTextContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalizedContentType = contentType.toLowerCase();
        return normalizedContentType.startsWith("text/")
                || normalizedContentType.contains("json")
                || normalizedContentType.contains("xml")
                || normalizedContentType.contains("form-urlencoded");
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

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            if (stringValue.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(stringValue);
            } catch (IOException ex) {
                return objectMapper.getNodeFactory().textNode(stringValue);
            }
        }
        return objectMapper.valueToTree(value);
    }
}

