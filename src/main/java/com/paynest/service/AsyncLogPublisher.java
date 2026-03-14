package com.paynest.service;

import com.paynest.dto.logging.ApiAuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncLogPublisher {

    private final KafkaTemplate<String, ApiAuditLogEvent> kafkaTemplate;

    @Value("${spring.app.kafka.audit-enabled:true}")
    private boolean auditEnabled;

    @Value("${spring.app.kafka.audit-topic:paynest.api.audit}")
    private String auditTopic;

    public void publish(ApiAuditLogEvent event) {
        if (!auditEnabled || event == null) {
            return;
        }

        String key = event.getCorrelationId() == null ? "unknown" : event.getCorrelationId();
        kafkaTemplate.send(auditTopic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish API audit event to Kafka. correlationId={}", key, ex);
            }
        });
    }
}
