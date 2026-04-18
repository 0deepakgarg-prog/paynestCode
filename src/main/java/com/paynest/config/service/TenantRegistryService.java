package com.paynest.config.service;

import com.paynest.payments.service.BillPaymentStatusSchemaInitializer;
import com.paynest.payments.service.ServiceCatalogSchemaInitializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistryService {

    private final JdbcTemplate jdbcTemplate;
    private final BillPaymentStatusSchemaInitializer billPaymentStatusSchemaInitializer;
    private final ServiceCatalogSchemaInitializer serviceCatalogSchemaInitializer;

    private final Map<String, String> tenantSchemaMap =
            new ConcurrentHashMap<>();
    private final Map<String, String> tenantTimeZoneMap =
            new ConcurrentHashMap<>();
    private final Map<String, String> schemaTimeZoneMap =
            new ConcurrentHashMap<>();

    @PostConstruct
    public synchronized void loadTenants() {
        tenantSchemaMap.clear();
        tenantTimeZoneMap.clear();
        schemaTimeZoneMap.clear();

        loadTenantRows().forEach(t -> {
            tenantSchemaMap.put(
                    readString(t, "tenant_id"),
                    readString(t, "schema_name")
            );
            String timeZone = normalizeTimeZone(readString(t, "iana_time_zone"));
            tenantTimeZoneMap.put(readString(t, "tenant_id"), timeZone);
            schemaTimeZoneMap.put(readString(t, "schema_name"), timeZone);
        });

        billPaymentStatusSchemaInitializer.ensureTableExistsForSchemas(tenantSchemaMap.values());
        serviceCatalogSchemaInitializer.ensureTableExistsForSchemas(tenantSchemaMap.values());
        log.info("Loaded {} tenant mappings", tenantSchemaMap.size());
    }

    public void ensureTenantsLoaded() {
        if (!tenantSchemaMap.isEmpty()) {
            return;
        }

        synchronized (this) {
            if (tenantSchemaMap.isEmpty()) {
                log.info("Tenant cache empty before request processing. Loading tenants now.");
                loadTenants();
            }
        }
    }

    public String getSchema(String tenantId) {
        return tenantSchemaMap.get(tenantId);
    }

    public String getTimeZone(String tenantId) {
        return tenantTimeZoneMap.getOrDefault(tenantId, "UTC");
    }

    public String getTimeZoneBySchema(String schemaName) {
        return schemaTimeZoneMap.getOrDefault(schemaName, "UTC");
    }

    private List<Map<String, Object>> loadTenantRows() {
        String sql = hasIanaTimeZoneColumn()
                ? "SELECT tenant_id, schema_name, iana_time_zone FROM public.tenant_registry"
                : "SELECT tenant_id, schema_name FROM public.tenant_registry";
        return jdbcTemplate.queryForList(sql);
    }

    private boolean hasIanaTimeZoneColumn() {
        Boolean columnExists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'tenant_registry'
                      AND column_name = 'iana_time_zone'
                )
                """, Boolean.class);
        return Boolean.TRUE.equals(columnExists);
    }

    private String readString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private String normalizeTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return "UTC";
        }
        String normalizedTimeZone = timeZone.trim();
        try {
            return ZoneId.of(normalizedTimeZone).getId();
        } catch (Exception ex) {
            log.warn("Unsupported tenant IANA time zone '{}'. Falling back to UTC", normalizedTimeZone);
            return "UTC";
        }
    }
}

