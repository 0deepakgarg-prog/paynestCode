package com.paynest.payments.bulkpayment.service;

import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkSalaryBatchScheduler {

    private final TenantRegistryService tenantRegistryService;
    private final BulkSalaryBatchService bulkSalaryBatchService;

    @Scheduled(fixedDelayString = "${bulk.salary.scheduler.fixed-delay-ms:60000}")
    public void processApprovedSalaryBatches() {
        tenantRegistryService.ensureTenantsLoaded();
        tenantRegistryService.getTenantSchemaMap().forEach((tenantId, schemaName) -> {
            try {
                TenantContext.setTenant(schemaName);
                TenantContext.setTenantId(tenantId);
                TenantContext.setTimeZone(tenantRegistryService.getTimeZone(tenantId));
                bulkSalaryBatchService.processNextApprovedBatchIfPossible();
            } catch (Exception ex) {
                log.error("Failed scheduled bulk salary processing for tenantId={}, schema={}", tenantId, schemaName, ex);
            } finally {
                TenantContext.clear();
            }
        });
    }
}
