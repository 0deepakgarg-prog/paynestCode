package com.paynest.service;

import com.paynest.repository.TenantRegistryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
    public void loadTenants() {

        repository.findAll().forEach(t ->
                tenantSchemaMap.put(
                        t.getTenantId(),
                        t.getSchemaName()
                ));
    }

    public String getSchema(String tenantId) {
        return tenantSchemaMap.get(tenantId);
    }
}
