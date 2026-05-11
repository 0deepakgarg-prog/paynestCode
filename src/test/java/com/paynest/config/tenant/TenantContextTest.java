package com.paynest.config.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setTenantId_updatesLogbackMdcTenantId() {
        TenantContext.setTenantId("tenant-1");

        assertEquals("tenant-1", TenantContext.getTenantId());
        assertEquals("tenant-1", MDC.get("tenantId"));
    }

    @Test
    void setTenantId_sanitizesTenantIdForLogPath() {
        TenantContext.setTenantId("../tenant 1");

        assertEquals("../tenant 1", TenantContext.getTenantId());
        assertEquals(".._tenant_1", MDC.get("tenantId"));
    }

    @Test
    void clear_removesMdcTenantId() {
        TenantContext.setTenantId("tenant-1");

        TenantContext.clear();

        assertNull(TenantContext.getTenantId());
        assertNull(MDC.get("tenantId"));
    }
}
