package com.paynest.payments.service;

import com.paynest.config.service.TenantRegistryService;
import jakarta.annotation.PostConstruct;
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
public class RecentRecipientSchemaInitializer {

    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataSource dataSource;
    private final TenantRegistryService tenantRegistryService;

    @PostConstruct
    public void initializeKnownTenantSchemas() {
        tenantRegistryService.ensureTenantsLoaded();
      //  ensureTableExistsForSchemas(tenantRegistryService.getTenantSchemaMap().values());
    }

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
            statement.execute(buildRecentIndexSql(normalizedSchemaName));
            statement.execute(buildRecipientIndexSql(normalizedSchemaName));
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize recent_recipients table for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured recent_recipients table exists in schema {}", normalizedSchemaName);
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
                CREATE TABLE IF NOT EXISTS %s.recent_recipients (
                    account_id VARCHAR(30) NOT NULL,
                    recipient_account_id VARCHAR(30) NOT NULL,
                    service_code VARCHAR(15) NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    wallet_type VARCHAR(50) NOT NULL,
                    recipient_account_type VARCHAR(50),
                    recipient_identifier_type VARCHAR(30),
                    recipient_identifier_value VARCHAR(100),
                    recipient_display_name VARCHAR(200),
                    last_transaction_id VARCHAR(30),
                    last_paid_at TIMESTAMP NOT NULL,
                    payment_count BIGINT NOT NULL DEFAULT 1,
                    field1 VARCHAR(250),
                    field2 VARCHAR(250),
                    field3 VARCHAR(250),
                    field4 VARCHAR(250),
                    field5 VARCHAR(250),
                    created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    modified_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (account_id, recipient_account_id, service_code, currency, wallet_type)
                )
                """.formatted(schemaName);
    }

    private String buildRecentIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_recent_recipients_account_last_paid
                    ON %s.recent_recipients(account_id, last_paid_at DESC)
                """.formatted(schemaName);
    }

    private String buildRecipientIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_recent_recipients_account_service_last_paid
                    ON %s.recent_recipients(account_id, service_code, last_paid_at DESC)
                """.formatted(schemaName);
    }
}
