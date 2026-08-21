package com.paynest.notifications.service;

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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxSchemaInitializer {

    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataSource dataSource;
    private final TenantRegistryService tenantRegistryService;

    @PostConstruct
    public void initializeKnownTenantSchemas() {
        tenantRegistryService.ensureTenantsLoaded();
        ensureTableExistsForSchemas(tenantRegistryService.getTenantSchemaMap().values());
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
        try (Connection connection = dataSource.getConnection()) {
            if (!schemaExists(connection, normalizedSchemaName)) {
                log.warn("Skipping notification_outbox table initialization because schema {} does not exist",
                        normalizedSchemaName);
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(buildCreateTableSql(normalizedSchemaName));
                executeIndexStatement(
                        statement,
                        buildPendingIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_notification_outbox_pending"
                );
                executeIndexStatement(
                        statement,
                        buildTransactionIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_notification_outbox_transaction"
                );
                executeIndexStatement(
                        statement,
                        buildChannelStatusIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_notification_outbox_channel_status"
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize notification_outbox table for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured notification_outbox table exists in schema {}", normalizedSchemaName);
    }

    private void executeIndexStatement(
            Statement statement,
            String sql,
            String schemaName,
            String indexName
    ) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException ex) {
            if (isPostgresOwnershipOrPrivilegeError(ex)) {
                log.warn(
                        "Skipping notification_outbox index {} in schema {} because the current database user is not the table owner",
                        indexName,
                        schemaName
                );
                return;
            }
            throw ex;
        }
    }

    private boolean isPostgresOwnershipOrPrivilegeError(SQLException ex) {
        for (SQLException current = ex; current != null; current = current.getNextException()) {
            if ("42501".equals(current.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("must be owner")) {
                return true;
            }
        }
        return false;
    }

    private boolean schemaExists(Connection connection, String schemaName) throws SQLException {
        try (var resultSet = connection.getMetaData().getSchemas(null, schemaName)) {
            return resultSet.next();
        }
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
                CREATE TABLE IF NOT EXISTS %s.notification_outbox (
                    notification_id BIGSERIAL PRIMARY KEY,
                    transaction_id VARCHAR(30),
                    account_id VARCHAR(100),
                    party_role VARCHAR(20),
                    channel VARCHAR(50) NOT NULL,
                    recipient VARCHAR(2000) NOT NULL,
                    recipient_masked VARCHAR(200),
                    template_code VARCHAR(200),
                    subject VARCHAR(500),
                    title VARCHAR(500),
                    notification_text TEXT NOT NULL,
                    payload JSONB,
                    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    next_attempt_at TIMESTAMP,
                    last_error VARCHAR(1000),
                    service_code VARCHAR(15),
                    transfer_status VARCHAR(10),
                    trace_id VARCHAR(100),
                    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    sent_on TIMESTAMP,
                    version BIGINT
                )
                """.formatted(schemaName);
    }

    private String buildPendingIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending
                    ON %s.notification_outbox(status, next_attempt_at, created_on)
                """.formatted(schemaName);
    }

    private String buildTransactionIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_notification_outbox_transaction
                    ON %s.notification_outbox(transaction_id)
                """.formatted(schemaName);
    }

    private String buildChannelStatusIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_notification_outbox_channel_status
                    ON %s.notification_outbox(channel, status, created_on)
                """.formatted(schemaName);
    }
}
