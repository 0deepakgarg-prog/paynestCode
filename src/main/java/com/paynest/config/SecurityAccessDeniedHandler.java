package com.paynest.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        String errorMessage = accessDeniedException == null || accessDeniedException.getMessage() == null
                ? "Access denied"
                : accessDeniedException.getMessage();

        log.error("Spring Security error. code=ACCESS_DENIED, message={}", errorMessage, accessDeniedException);

        FilterErrorResponseWriter.write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "ACCESS_DENIED",
                errorMessage
        );
    }
}

