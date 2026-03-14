package com.paynest.service;

import com.paynest.entity.TenantRegistry;
import com.paynest.repository.TenantRegistryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRegistryServiceTest {

    @Mock
    private TenantRegistryRepository repository;

    @InjectMocks
    private TenantRegistryService tenantRegistryService;

    @Test
    void loadTenants_shouldPopulateTenantSchemaMap() {
        TenantRegistry t1 = new TenantRegistry();
        t1.setTenantId("TENANT_A");
        t1.setSchemaName("schema_a");

        TenantRegistry t2 = new TenantRegistry();
        t2.setTenantId("TENANT_B");
        t2.setSchemaName("schema_b");

        when(repository.findAll()).thenReturn(List.of(t1, t2));

        tenantRegistryService.loadTenants();

        assertEquals("schema_a", tenantRegistryService.getSchema("TENANT_A"));
        assertEquals("schema_b", tenantRegistryService.getSchema("TENANT_B"));
    }

    @Test
    void getSchema_shouldReturnNull_whenTenantMissing() {
        when(repository.findAll()).thenReturn(List.of());
        tenantRegistryService.loadTenants();

        assertNull(tenantRegistryService.getSchema("UNKNOWN_TENANT"));
    }
}
