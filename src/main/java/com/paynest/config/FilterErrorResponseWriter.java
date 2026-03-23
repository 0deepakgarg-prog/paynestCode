package com.paynest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.exception.ApplicationException;
import com.paynest.config.tenant.TraceContext;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FilterErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FilterErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response,
                             int httpStatus,
                             String errorCode,
                             String errorMessage) throws IOException {

        response.setStatus(httpStatus);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("responseStatus", TransactionStatus.FAILURE);
        body.put("code", errorCode);
        body.put("message", errorMessage);
        body.put("traceId", TraceContext.getTraceId());
        body.put("timestamp", LocalDateTime.now().toString());

        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
        response.getWriter().flush();
    }

    public static int resolveHttpStatus(Exception ex) {
        if (ex instanceof ApplicationException) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
}

