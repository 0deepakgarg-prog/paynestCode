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
public class NotificationTemplateSchemaInitializer {

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
            statement.execute(buildTemplateCodeIndexSql(normalizedSchemaName));
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize notification_template table for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured notification_template table exists in schema {}", normalizedSchemaName);
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
                CREATE TABLE IF NOT EXISTS %s.notification_template (
                    template_id BIGSERIAL PRIMARY KEY,
                    template_code VARCHAR(200) NOT NULL,
                    template_definition JSONB NOT NULL,
                    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                    description VARCHAR(500),
                    created_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """.formatted(schemaName);
    }

    private String buildTemplateCodeIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_notification_template_code_status
                    ON %s.notification_template (template_code, status)
                """.formatted(schemaName);
    }
}
