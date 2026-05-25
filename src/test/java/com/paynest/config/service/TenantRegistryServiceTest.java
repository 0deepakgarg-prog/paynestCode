package com.paynest.config.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRegistryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TenantRegistryService tenantRegistryService;

    @Test
    void loadTenants_shouldPopulateTenantSchemaMap() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbcTemplate.queryForList("SELECT tenant_id, schema_name, iana_time_zone FROM public.tenant_registry"))
                .thenReturn(List.of(
                        Map.of(
                                "tenant_id", "TENANT_A",
                                "schema_name", "schema_a",
                                "iana_time_zone", "Europe/Chisinau"
                        ),
                        Map.of(
                                "tenant_id", "TENANT_B",
                                "schema_name", "schema_b",
                                "iana_time_zone", "Invalid/Zone"
                        )
                ));

        tenantRegistryService.loadTenants();

        assertEquals("schema_a", tenantRegistryService.getSchema("TENANT_A"));
        assertEquals("schema_b", tenantRegistryService.getSchema("TENANT_B"));
        assertEquals("Europe/Chisinau", tenantRegistryService.getTimeZone("TENANT_A"));
        assertEquals("UTC", tenantRegistryService.getTimeZone("TENANT_B"));
        assertEquals("Europe/Chisinau", tenantRegistryService.getTimeZoneBySchema("schema_a"));
    }

    @Test
    void getSchema_shouldReturnNull_whenTenantMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        when(jdbcTemplate.queryForList("SELECT tenant_id, schema_name FROM public.tenant_registry"))
                .thenReturn(List.of());
        tenantRegistryService.loadTenants();

        assertNull(tenantRegistryService.getSchema("UNKNOWN_TENANT"));
    }
}

