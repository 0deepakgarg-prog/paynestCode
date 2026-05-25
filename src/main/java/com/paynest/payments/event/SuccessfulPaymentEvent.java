package com.paynest.payments.event;

import com.paynest.payments.entity.Transactions;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class SuccessfulPaymentEvent extends ApplicationEvent {

    private final Transactions transaction;
    private final String tenantSchema;
    private final String tenantId;
    private final String tenantTimeZone;
    private final String traceId;
    private final Map<String, String> mdcContextMap;
    private final AtomicBoolean listenerDeliveryStarted = new AtomicBoolean(false);

    @Builder
    public SuccessfulPaymentEvent(
            Object source,
            Transactions transaction,
            String tenantSchema,
            String tenantId,
            String tenantTimeZone,
            String traceId,
            Map<String, String> mdcContextMap
    ) {
        super(source == null ? SuccessfulPaymentEvent.class.getName() : source);
        this.transaction = transaction;
        this.tenantSchema = tenantSchema;
        this.tenantId = tenantId;
        this.tenantTimeZone = tenantTimeZone;
        this.traceId = traceId;
        this.mdcContextMap = mdcContextMap == null ? Map.of() : Map.copyOf(mdcContextMap);
    }

    public boolean markListenerDeliveryStarted() {
        return listenerDeliveryStarted.compareAndSet(false, true);
    }
}
