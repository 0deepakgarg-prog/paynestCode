package com.paynest.notifications.service;

import com.paynest.config.service.TenantRegistryService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NotificationOutboxSchemaInitializerTest {

    @Test
    void ensureTableExistsForSchemas_shouldCreateTableOncePerDistinctSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tenantOneSchema = mock(ResultSet.class);
        ResultSet tenantTwoSchema = mock(ResultSet.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(null, "tenant_one")).thenReturn(tenantOneSchema);
        when(metaData.getSchemas(null, "tenant_two")).thenReturn(tenantTwoSchema);
        when(tenantOneSchema.next()).thenReturn(true);
        when(tenantTwoSchema.next()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);

        NotificationOutboxSchemaInitializer initializer =
                new NotificationOutboxSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTableExistsForSchemas(List.of("tenant_one", "tenant_one", "tenant_two"));

        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.notification_outbox"));
        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_two.notification_outbox"));
        verify(statement).execute(contains("ON tenant_one.notification_outbox(status, next_attempt_at, created_on)"));
        verify(statement).execute(contains("ON tenant_two.notification_outbox(status, next_attempt_at, created_on)"));
        verify(statement).execute(contains("ON tenant_one.notification_outbox(transaction_id)"));
        verify(statement).execute(contains("ON tenant_one.notification_outbox(channel, status, created_on)"));
    }

    @Test
    void ensureTableExistsForSchemas_shouldContinueWhenIndexCreationNeedsTableOwner() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet schema = mock(ResultSet.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(null, "tenant_one")).thenReturn(schema);
        when(schema.next()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(contains("CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending")))
                .thenThrow(new SQLException("ERROR: must be owner of table notification_outbox", "42501"));

        NotificationOutboxSchemaInitializer initializer =
                new NotificationOutboxSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTableExistsForSchemas(List.of("tenant_one"));

        verify(statement).execute(contains("CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending"));
        verify(statement).execute(contains("CREATE INDEX IF NOT EXISTS idx_notification_outbox_transaction"));
    }

    @Test
    void ensureTableExistsForSchemas_shouldSkipMissingSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet missingSchema = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(null, "tenant_missing")).thenReturn(missingSchema);
        when(missingSchema.next()).thenReturn(false);

        NotificationOutboxSchemaInitializer initializer =
                new NotificationOutboxSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTableExistsForSchemas(List.of("tenant_missing"));

        verify(connection, never()).createStatement();
    }

    @Test
    void ensureTableExistsForSchemas_shouldRejectUnsafeSchemaNames() {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        NotificationOutboxSchemaInitializer initializer =
                new NotificationOutboxSchemaInitializer(dataSource, tenantRegistryService);

        assertThrows(
                IllegalArgumentException.class,
                () -> initializer.ensureTableExistsForSchemas(List.of("tenant-one"))
        );

        verifyNoMoreInteractions(dataSource);
    }
}
