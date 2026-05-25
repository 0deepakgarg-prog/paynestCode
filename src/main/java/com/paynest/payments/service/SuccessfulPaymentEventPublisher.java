package com.paynest.payments.service;

import com.paynest.common.Constants;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.event.SuccessfulPaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuccessfulPaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(Transactions transaction) {
        if (transaction == null || !Constants.TRANSACTION_SUCCESS.equalsIgnoreCase(transaction.getTransferStatus())) {
            return;
        }

        SuccessfulPaymentEvent event = SuccessfulPaymentEvent.builder()
                .source(this)
                .transaction(transaction)
                .tenantSchema(TenantContext.getTenant())
                .tenantId(TenantContext.getTenantId())
                .tenantTimeZone(TenantContext.getTimeZone())
                .traceId(resolveTraceId(transaction))
                .mdcContextMap(MDC.getCopyOfContextMap())
                .build();

        publishAfterCommit(event);
    }

    private void publishAfterCommit(SuccessfulPaymentEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(event);
            }
        });
    }

    private void dispatch(SuccessfulPaymentEvent event) {
        Transactions transaction = event.getTransaction();
        log.debug(
                "Dispatching successful payment event. transactionId={}, serviceCode={}, tenantId={}, tenantSchema={}",
                transaction == null ? null : transaction.getTransactionId(),
                transaction == null ? null : transaction.getServiceCode(),
                event.getTenantId(),
                event.getTenantSchema()
        );
        applicationEventPublisher.publishEvent(event);
    }

    private String resolveTraceId(Transactions transaction) {
        String traceId = TraceContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return transaction.getTraceId();
    }
}
