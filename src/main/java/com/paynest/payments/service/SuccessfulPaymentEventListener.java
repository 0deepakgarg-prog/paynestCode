package com.paynest.payments.service;

import com.paynest.payments.event.SuccessfulPaymentEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuccessfulPaymentEventListener implements ApplicationListener<SuccessfulPaymentEvent> {

    private final AsyncSuccessfulPaymentProcessor asyncSuccessfulPaymentProcessor;
    private final ApplicationEventMulticaster applicationEventMulticaster;

    @PostConstruct
    public void init() {
        applicationEventMulticaster.addApplicationListener(this);
        log.info("SuccessfulPaymentEventListener initialized and registered");
    }

    @Override
    public void onApplicationEvent(SuccessfulPaymentEvent event) {
        if (!event.markListenerDeliveryStarted()) {
            return;
        }
        asyncSuccessfulPaymentProcessor.process(event);
    }
}
