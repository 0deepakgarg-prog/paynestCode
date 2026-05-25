package com.paynest.payments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceCatalogSchemaInitializer {

    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataSource dataSource;

    public void ensureTableExistsForSchemas(Collection<String> schemaNames) {
        if (schemaNames == null || schemaNames.isEmpty()) {
            return;
        }

        Set<String> distinctSchemas = new LinkedHashSet<>();
        for (String schemaName : schemaNames) {
            if (schemaName != null && !schemaName.isBlank()) {
                distinctSchemas.add(schemaName.trim());
            }
        }

        for (String schemaName : distinctSchemas) {
            ensureTableExists(schemaName);
        }
    }

    void ensureTableExists(String schemaName) {
        String normalizedSchemaName = normalizeSchemaName(schemaName);
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.execute(buildCreateTableSql(normalizedSchemaName));
            statement.execute(buildAddSendToIntegratorColumnSql(normalizedSchemaName));
            statement.execute(buildAddRequiresConfirmationColumnSql(normalizedSchemaName));
            statement.execute(buildAddIntegratorCallModeColumnSql(normalizedSchemaName));
            statement.execute(buildSeedDataSql(normalizedSchemaName));
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize service_catalog table for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured service_catalog table exists in schema {}", normalizedSchemaName);
    }

    private String normalizeSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name must not be blank");
        }

        String normalizedSchemaName = schemaName.trim();
        if (!SAFE_SCHEMA_NAME.matcher(normalizedSchemaName).matches()) {
            throw new IllegalArgumentException("Unsupported schema name: " + normalizedSchemaName);
        }

        return normalizedSchemaName;
    }

    private String buildCreateTableSql(String schemaName) {
        return """
                CREATE TABLE IF NOT EXISTS %s.service_catalog (
                    service_code VARCHAR(50) PRIMARY KEY,
                    service_name VARCHAR(100) NOT NULL,
                    description VARCHAR(255),
                    service_category VARCHAR(50),
                    transaction_type VARCHAR(50),
                    is_financial BOOLEAN NOT NULL DEFAULT TRUE,
                    send_to_integrator BOOLEAN NOT NULL DEFAULT FALSE,
                    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
                    integrator_call_mode VARCHAR(20) NOT NULL DEFAULT 'SYNC',
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    display_order INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """.formatted(schemaName);
    }

    private String buildAddSendToIntegratorColumnSql(String schemaName) {
        return """
                ALTER TABLE %s.service_catalog
                    ADD COLUMN IF NOT EXISTS send_to_integrator BOOLEAN NOT NULL DEFAULT FALSE
                """.formatted(schemaName);
    }

    private String buildAddRequiresConfirmationColumnSql(String schemaName) {
        return """
                ALTER TABLE %s.service_catalog
                    ADD COLUMN IF NOT EXISTS requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE
                """.formatted(schemaName);
    }

    private String buildAddIntegratorCallModeColumnSql(String schemaName) {
        return """
                ALTER TABLE %s.service_catalog
                    ADD COLUMN IF NOT EXISTS integrator_call_mode VARCHAR(20) NOT NULL DEFAULT 'SYNC'
                """.formatted(schemaName);
    }

    private String buildSeedDataSql(String schemaName) {
        return """
                INSERT INTO %s.service_catalog
                    (service_code, service_name, description, service_category, transaction_type, is_financial, send_to_integrator, requires_confirmation, integrator_call_mode, display_order)
                VALUES
                    ('U2U', 'User Transfer', 'Wallet to wallet user transfer', 'PAYMENT', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 10),
                    ('MERCHANTPAY', 'Merchant Payment', 'Payment to merchant account', 'PAYMENT', 'MERCHANT', TRUE, FALSE, FALSE, 'SYNC', 20),
                    ('CASHIN', 'Cash In', 'Cash deposit into wallet', 'CASH', 'CREDIT', TRUE, FALSE, FALSE, 'SYNC', 30),
                    ('CASHOUT', 'Cash Out', 'Cash withdrawal from wallet', 'CASH', 'DEBIT', TRUE, FALSE, FALSE, 'SYNC', 40),
                    ('BILLPAY', 'Bill Payment', 'Bill payment transaction', 'PAYMENT', 'BILL', TRUE, FALSE, TRUE, 'SYNC', 50),
                    ('O2C', 'Operator to Channel Transfer', 'Operator wallet transfer to channel account', 'PAYMENT', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 60),
                    ('ACCOUNT_DELETION', 'Account Deletion', 'Balance movement during account deletion', 'SYSTEM', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 70),
                    ('R2U', 'Registered to Unregistered Transfer', 'Subscriber transfer to unregistered receiver holding wallet', 'PAYMENT', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 80),
                    ('CASHOUT_BY_CODE', 'Cashout by Code', 'Agent cashout using unregistered receiver passcode', 'CASH', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 90),
                    ('IPSP2P', 'Internal P2P Transfer', 'Internal subscriber to subscriber transfer', 'PAYMENT', 'TRANSFER', TRUE, FALSE, FALSE, 'SYNC', 100),
                    ('IPSMP', 'Internal Subscriber Merchant Payment', 'Internal transfer from subscriber to merchant', 'PAYMENT', 'PAYMENT', TRUE, FALSE, FALSE, 'SYNC', 110),
                    ('IPSCIN', 'Internal Agent Cash In', 'Internal transfer from agent to subscriber', 'PAYMENT', 'CREDIT', TRUE, FALSE, FALSE, 'SYNC', 120),
                    ('IPSBP', 'Third Party Bill Payment', 'Bill payment for third party requiring integrator confirmation', 'PAYMENT', 'PAYMENT', TRUE, TRUE, TRUE, 'ASYNC', 130),
                    ('IPSBPSC', 'Third Party Bill Payment Sync', 'Synchronous third party bill payment', 'PAYMENT', 'PAYMENT', TRUE, TRUE, FALSE, 'SYNC', 140)
                ON CONFLICT (service_code) DO UPDATE
                SET service_name = EXCLUDED.service_name,
                    description = EXCLUDED.description,
                    service_category = EXCLUDED.service_category,
                    transaction_type = EXCLUDED.transaction_type,
                    is_financial = EXCLUDED.is_financial,
                    send_to_integrator = EXCLUDED.send_to_integrator,
                    requires_confirmation = EXCLUDED.requires_confirmation,
                    integrator_call_mode = EXCLUDED.integrator_call_mode,
                    is_active = TRUE,
                    display_order = EXCLUDED.display_order,
                    updated_at = CURRENT_TIMESTAMP
                """.formatted(schemaName);
    }
}
