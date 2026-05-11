package com.paynest.users.service;

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
public class AccountNotificationEndpointSchemaInitializer {

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
            statement.execute(buildAccountTypeIndexSql(normalizedSchemaName));
            statement.execute(buildPrimaryEndpointIndexSql(normalizedSchemaName));
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize account_notification_endpoint table for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured account_notification_endpoint table exists in schema {}", normalizedSchemaName);
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
                CREATE TABLE IF NOT EXISTS %s.account_notification_endpoint (
                    account_endpoint_id BIGSERIAL PRIMARY KEY,
                    account_id VARCHAR(100) NOT NULL,
                    endpoint_type VARCHAR(50) NOT NULL,
                    endpoint_value VARCHAR(2000) NOT NULL,
                    is_primary BOOLEAN DEFAULT FALSE,
                    status VARCHAR(30) DEFAULT 'ACTIVE',
                    created_on TIMESTAMP DEFAULT NOW(),
                    updated_at TIMESTAMP DEFAULT NOW(),
                    field1 VARCHAR(250) NULL,
                    field2 VARCHAR(250) NULL,
                    field3 VARCHAR(250) NULL,
                    field4 VARCHAR(250) NULL,
                    field5 VARCHAR(250) NULL
                )
                """.formatted(schemaName);
    }

    private String buildAccountTypeIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_account_notification_endpoint_account_type
                    ON %s.account_notification_endpoint (account_id, endpoint_type)
                """.formatted(schemaName);
    }

    private String buildPrimaryEndpointIndexSql(String schemaName) {
        return """
                CREATE UNIQUE INDEX IF NOT EXISTS uq_account_notification_endpoint_primary
                    ON %s.account_notification_endpoint (account_id, endpoint_type)
                    WHERE is_primary = TRUE
                """.formatted(schemaName);
    }
}
