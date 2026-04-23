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
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    display_order INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """.formatted(schemaName);
    }

    private String buildSeedDataSql(String schemaName) {
        return """
                INSERT INTO %s.service_catalog
                    (service_code, service_name, description, service_category, transaction_type, display_order)
                VALUES
                    ('U2U', 'User Transfer', 'Wallet to wallet user transfer', 'PAYMENT', 'TRANSFER', 10),
                    ('MERCHANTPAY', 'Merchant Payment', 'Payment to merchant account', 'PAYMENT', 'MERCHANT', 20),
                    ('CASHIN', 'Cash In', 'Cash deposit into wallet', 'CASH', 'CREDIT', 30),
                    ('CASHOUT', 'Cash Out', 'Cash withdrawal from wallet', 'CASH', 'DEBIT', 40),
                    ('BILLPAY', 'Bill Payment', 'Bill payment transaction', 'PAYMENT', 'BILL', 50),
                    ('ACCOUNT_DELETION', 'Account Deletion', 'Balance movement during account deletion', 'SYSTEM', 'TRANSFER', 60)
                ON CONFLICT (service_code) DO NOTHING
                """.formatted(schemaName);
    }
}
