package com.paynest.payments.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServiceCatalogSchemaInitializerTest {

    @Test
    void ensureTableExistsForSchemas_shouldCreateAndSeedTableOncePerDistinctSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        ServiceCatalogSchemaInitializer initializer = new ServiceCatalogSchemaInitializer(dataSource);

        initializer.ensureTableExistsForSchemas(List.of("tenant_one", "tenant_one", "tenant_two"));

        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.service_catalog"));
        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_two.service_catalog"));
        verify(statement).execute(contains("INSERT INTO tenant_one.service_catalog"));
        verify(statement).execute(contains("INSERT INTO tenant_two.service_catalog"));
    }

    @Test
    void ensureTableExistsForSchemas_shouldRejectUnsafeSchemaNames() {
        DataSource dataSource = mock(DataSource.class);
        ServiceCatalogSchemaInitializer initializer = new ServiceCatalogSchemaInitializer(dataSource);

        assertThrows(
                IllegalArgumentException.class,
                () -> initializer.ensureTableExistsForSchemas(List.of("tenant-one"))
        );

        verifyNoMoreInteractions(dataSource);
    }
}
