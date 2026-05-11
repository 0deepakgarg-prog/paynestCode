package com.paynest.notifications.service;

import com.paynest.config.AsyncEventConfig;
import com.paynest.notifications.event.TransactionNotificationEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionNotificationEventListener implements ApplicationListener<TransactionNotificationEvent> {

    private final AsyncTransactionNotificationProcessor asyncTransactionNotificationProcessor;
    private final ApplicationEventMulticaster applicationEventMulticaster;

    @PostConstruct
    public void init() {
        applicationEventMulticaster.addApplicationListener(this);
        log.info(
                "TransactionNotificationEventListener initialized and registered with applicationEventMulticaster. asyncExecutor={}, listenerClass={}",
                AsyncEventConfig.NOTIFICATION_EVENT_EXECUTOR,
                getClass().getName()
        );
    }

    @Override
    public void onApplicationEvent(TransactionNotificationEvent event) {
        if (!event.markListenerDeliveryStarted()) {
            log.debug(
                    "Skipping duplicate transaction notification event listener delivery. transactionId={}, status={}",
                    event.getTransactionId(),
                    event.getTransferStatus()
            );
            return;
        }

        log.info(
                "Received transaction notification event. transactionId={}, status={}, serviceCode={}, traceId={}, tenantId={}, tenantSchema={}, thread={}",
                event.getTransactionId(),
                event.getTransferStatus(),
                event.getServiceCode(),
                event.getTraceId(),
                event.getTenantId(),
                event.getTenantSchema(),
                Thread.currentThread().getName()
        );
        asyncTransactionNotificationProcessor.process(event);
    }
}
