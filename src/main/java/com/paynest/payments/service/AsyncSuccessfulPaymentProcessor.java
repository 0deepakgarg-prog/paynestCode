package com.paynest.payments.service;

import com.paynest.config.AsyncEventConfig;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.event.SuccessfulPaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncSuccessfulPaymentProcessor {

    private final RecentRecipientService recentRecipientService;

    @Async(AsyncEventConfig.NOTIFICATION_EVENT_EXECUTOR)
    public void process(SuccessfulPaymentEvent event) {
        try {
            restoreAsyncContext(event);
            Transactions transaction = event.getTransaction();
            recentRecipientService.recordSuccessfulPayment(transaction);
        } catch (Exception ex) {
            Transactions transaction = event.getTransaction();
            log.error(
                    "Failed to process successful payment event. transactionId={}",
                    transaction == null ? null : transaction.getTransactionId(),
                    ex
            );
        } finally {
            clearAsyncContext();
        }
    }

    private void restoreAsyncContext(SuccessfulPaymentEvent event) {
        if (event.getMdcContextMap() != null && !event.getMdcContextMap().isEmpty()) {
            MDC.setContextMap(event.getMdcContextMap());
        }
        TenantContext.setTenant(event.getTenantSchema());
        TenantContext.setTenantId(event.getTenantId());
        TenantContext.setTimeZone(event.getTenantTimeZone());
        TraceContext.setTraceId(event.getTraceId());
    }

    private void clearAsyncContext() {
        TenantContext.clear();
        TraceContext.clear();
        MDC.clear();
    }
}
