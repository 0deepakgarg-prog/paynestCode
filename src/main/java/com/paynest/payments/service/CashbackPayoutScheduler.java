package com.paynest.payments.service;

import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TenantTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CashbackPayoutScheduler {

    private final TenantRegistryService tenantRegistryService;
    private final CashbackPayoutService cashbackPayoutService;

    @Scheduled(cron = "0 0 * * * *")
    public void payoutDueCashbackHourly() {
        tenantRegistryService.ensureTenantsLoaded();
        tenantRegistryService.getTenantSchemaMap().forEach((tenantId, schemaName) -> {
            try {
                TenantContext.setTenant(schemaName);
                TenantContext.setTenantId(tenantId);
                TenantContext.setTimeZone(tenantRegistryService.getTimeZone(tenantId));
                cashbackPayoutService.payoutDueCashback(TenantTime.now());
            } catch (Exception ex) {
                log.error("Failed hourly cashback payout for tenantId={}, schema={}", tenantId, schemaName, ex);
            } finally {
                TenantContext.clear();
            }
        });
    }
}
