package com.paynest.dto.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAuditLogEvent {
    private String eventType;
    private String correlationId;
    private LocalDateTime eventTime;

    private String method;
    private String path;
    private String query;
    private Integer status;
    private Long durationMs;

    private String tenantId;
    private String accountId;
    private String clientIp;

    private Map<String, String> headers;
    private String requestBody;
    private String responseBody;
}
