package com.paynest.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EndpointStartupLogger {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguredEndpoints() {
        List<String> endpointLines = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .map(this::toEndpointLine)
                .sorted(Comparator.naturalOrder())
                .toList();

        log.info("Configured endpoints count={}", endpointLines.size());
        endpointLines.forEach(line -> log.info("Endpoint {}", line));
    }

    private String toEndpointLine(java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> entry) {
        RequestMappingInfo mappingInfo = entry.getKey();
        org.springframework.web.method.HandlerMethod handlerMethod = entry.getValue();

        String paths = mappingInfo.getPathPatternsCondition() != null
                ? mappingInfo.getPathPatternsCondition().getPatternValues().toString()
                : "[]";

        String methods = mappingInfo.getMethodsCondition().getMethods().isEmpty()
                ? "[ALL]"
                : mappingInfo.getMethodsCondition().getMethods().toString();

        return methods + " " + paths + " -> "
                + handlerMethod.getBeanType().getSimpleName()
                + "#"
                + handlerMethod.getMethod().getName();
    }
}
