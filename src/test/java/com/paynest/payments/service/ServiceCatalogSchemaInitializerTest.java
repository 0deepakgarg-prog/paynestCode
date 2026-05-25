package com.paynest.payments.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
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
        verify(statement, atLeastOnce()).execute(contains("('IPSP2P', 'Internal P2P Transfer', 'Internal subscriber to subscriber transfer', 'PAYMENT', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 100)"));
        verify(statement, atLeastOnce()).execute(contains("('IPSMP', 'Internal Subscriber Merchant Payment', 'Internal transfer from subscriber to merchant', 'PAYMENT', 'PAYMENT', TRUE, FALSE, FALSE, 'SYNC', 110)"));
        verify(statement, atLeastOnce()).execute(contains("('IPSCIN', 'Internal Agent Cash In', 'Internal transfer from agent to subscriber', 'PAYMENT', 'CREDIT', TRUE, FALSE, FALSE, 'SYNC', 120)"));
        verify(statement, atLeastOnce()).execute(contains("('IPSBP', 'Third Party Bill Payment', 'Bill payment for third party requiring integrator confirmation', 'PAYMENT', 'PAYMENT', TRUE, TRUE, TRUE, 'ASYNC', 130)"));
        verify(statement, atLeastOnce()).execute(contains("('IPSBPSC', 'Third Party Bill Payment Sync', 'Synchronous third party bill payment', 'PAYMENT', 'PAYMENT', TRUE, TRUE, FALSE, 'SYNC', 140)"));
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
