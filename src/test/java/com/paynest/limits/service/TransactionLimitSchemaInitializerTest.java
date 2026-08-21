package com.paynest.limits.service;

import com.paynest.config.service.TenantRegistryService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
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

class TransactionLimitSchemaInitializerTest {

    @Test
    void ensureTablesExistForSchemas_shouldCreateLimitTablesOncePerDistinctExistingSchema() throws Exception {
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

        TransactionLimitSchemaInitializer initializer =
                new TransactionLimitSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTablesExistForSchemas(List.of("tenant_one", "tenant_one", "tenant_two"));

        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.transaction_limit_profile ("));
        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_two.transaction_limit_profile ("));
        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.transaction_limit_usage"));
        verify(statement).execute(contains("ON tenant_one.transaction_limit_usage(account_id, period_type"));
    }

    @Test
    void ensureTablesExistForSchemas_shouldSkipMissingSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet missingSchema = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(null, "tenant_missing")).thenReturn(missingSchema);
        when(missingSchema.next()).thenReturn(false);

        TransactionLimitSchemaInitializer initializer =
                new TransactionLimitSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTablesExistForSchemas(List.of("tenant_missing"));

        verify(connection, never()).createStatement();
    }

    @Test
    void ensureTablesExistForSchemas_shouldContinueWhenExistingTableCannotBeCreatedByNonOwner() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet schema = mock(ResultSet.class);
        ResultSet existingProfileTable = mock(ResultSet.class);
        PreparedStatement tableExistsStatement = mock(PreparedStatement.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(null, "tenant_one")).thenReturn(schema);
        when(schema.next()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.transaction_limit_profile (")))
                .thenThrow(new SQLException("ERROR: permission denied for table tags", "42501"));
        when(connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")).thenReturn(tableExistsStatement);
        when(tableExistsStatement.executeQuery()).thenReturn(existingProfileTable);
        when(existingProfileTable.next()).thenReturn(true);
        when(existingProfileTable.getBoolean(1)).thenReturn(true);

        TransactionLimitSchemaInitializer initializer =
                new TransactionLimitSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTablesExistForSchemas(List.of("tenant_one"));

        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.transaction_limit_profile ("));
        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS tenant_one.transaction_limit_profile_details"));
        verify(tableExistsStatement).setString(1, "tenant_one.transaction_limit_profile");
    }

    @Test
    void ensureTablesExistForSchemas_shouldContinueWhenIndexCreationNeedsTableOwner() throws Exception {
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
        when(statement.execute(contains("CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_type")))
                .thenThrow(new SQLException("ERROR: must be owner of table transaction_limit_profile", "42501"));

        TransactionLimitSchemaInitializer initializer =
                new TransactionLimitSchemaInitializer(dataSource, tenantRegistryService);

        initializer.ensureTablesExistForSchemas(List.of("tenant_one"));

        verify(statement).execute(contains("CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_type"));
        verify(statement).execute(contains("CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_tag"));
    }

    @Test
    void ensureTablesExistForSchemas_shouldRejectUnsafeSchemaNames() {
        DataSource dataSource = mock(DataSource.class);
        TenantRegistryService tenantRegistryService = mock(TenantRegistryService.class);
        TransactionLimitSchemaInitializer initializer =
                new TransactionLimitSchemaInitializer(dataSource, tenantRegistryService);

        assertThrows(
                IllegalArgumentException.class,
                () -> initializer.ensureTablesExistForSchemas(List.of("tenant-one"))
        );

        verifyNoMoreInteractions(dataSource);
    }
}
