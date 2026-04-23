package com.paynest.payments.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BillPaymentStatusSchemaInitializerTest {

    @Test
    void ensureTableExistsForSchemas_shouldCreateTableOncePerDistinctSchema() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        BillPaymentStatusSchemaInitializer initializer = new BillPaymentStatusSchemaInitializer(dataSource);

        initializer.ensureTableExistsForSchemas(List.of("tenant_one", "tenant_one", "tenant_two"));

        verify(statement).execute(org.mockito.ArgumentMatchers.contains("tenant_one.bill_payment_status"));
        verify(statement).execute(org.mockito.ArgumentMatchers.contains("tenant_two.bill_payment_status"));
    }

    @Test
    void ensureTableExistsForSchemas_shouldRejectUnsafeSchemaNames() {
        DataSource dataSource = mock(DataSource.class);
        BillPaymentStatusSchemaInitializer initializer = new BillPaymentStatusSchemaInitializer(dataSource);

        assertThrows(
                IllegalArgumentException.class,
                () -> initializer.ensureTableExistsForSchemas(List.of("tenant-one"))
        );

        verifyNoMoreInteractions(dataSource);
    }
}
