package com.paynest.service;

import com.paynest.repository.TenantRegistryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistryService {

    private final TenantRegistryRepository repository;

    private final Map<String, String> tenantSchemaMap =
            new ConcurrentHashMap<>();

    @PostConstruct
    public synchronized void loadTenants() {
        tenantSchemaMap.clear();

        repository.findAll().forEach(t ->
                tenantSchemaMap.put(
                        t.getTenantId(),
                        t.getSchemaName()
                ));

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
}
