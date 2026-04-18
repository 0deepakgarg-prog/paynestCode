
package com.paynest.config.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantContext {

    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);
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
    }
}

