
package com.paynest.config.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class TenantContext {

    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TIME_ZONE = new ThreadLocal<>();

    public static void setTenant(String tenant) {
        CURRENT.set(tenant);
    }

    public static String getTenant() {
        return CURRENT.get();
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
        MDC.put(MDC_TENANT_ID, sanitizeTenantId(tenantId));
    }

    public static String getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void setTimeZone(String timeZone) {
        CURRENT_TIME_ZONE.set(timeZone);
    }

    public static String getTimeZone() {
        return CURRENT_TIME_ZONE.get();
    }

    public static void clear() {
        CURRENT.remove();
        CURRENT_TENANT_ID.remove();
        CURRENT_TIME_ZONE.remove();
        MDC.remove(MDC_TENANT_ID);
    }

    private static String sanitizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return DEFAULT_TENANT_ID;
        }

        return tenantId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

