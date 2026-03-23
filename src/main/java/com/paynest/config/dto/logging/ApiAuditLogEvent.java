package com.paynest.config.dto.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize ApiAuditLogEvent to JSON", ex);
        }
    }
}

