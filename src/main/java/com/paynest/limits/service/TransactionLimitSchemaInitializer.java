package com.paynest.limits.service;

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
public class TransactionLimitSchemaInitializer {

    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataSource dataSource;
    private final TenantRegistryService tenantRegistryService;

    @PostConstruct
    public void initializeKnownTenantSchemas() {
        tenantRegistryService.ensureTenantsLoaded();
        ensureTablesExistForSchemas(tenantRegistryService.getTenantSchemaMap().values());
    }

    public void ensureTablesExistForSchemas(Collection<String> schemaNames) {
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
            ensureTablesExist(schemaName);
        }
    }

    void ensureTablesExist(String schemaName) {
        String normalizedSchemaName = normalizeSchemaName(schemaName);
        try (Connection connection = dataSource.getConnection()) {
            if (!schemaExists(connection, normalizedSchemaName)) {
                log.warn("Skipping transaction limit table initialization because schema {} does not exist",
                        normalizedSchemaName);
                return;
            }
            try (Statement statement = connection.createStatement()) {
                executeTableStatement(
                        connection,
                        statement,
                        buildProfileTableSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildDetailTableSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile_details"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildPeriodTableSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile_period"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildUsageTableSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_usage"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildProfileMigrationSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildUsageMigrationSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_usage"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildProfileStoredAmountScaleMigrationSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildDetailStoredAmountScaleMigrationSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile_details"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildPeriodStoredAmountScaleMigrationSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_profile_period"
                );
                executeTableStatement(
                        connection,
                        statement,
                        buildUsageStoredAmountScaleMigrationSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "transaction_limit_usage"
                );
                executeIndexStatement(
                        statement,
                        buildProfileTypeIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_transaction_limit_profile_type"
                );
                executeIndexStatement(
                        statement,
                        buildProfileTagIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_transaction_limit_profile_tag"
                );
                executeIndexStatement(
                        statement,
                        buildDetailProfileIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_transaction_limit_details_profile"
                );
                executeIndexStatement(
                        statement,
                        buildPeriodDetailIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_transaction_limit_period_details"
                );
                executeIndexStatement(
                        statement,
                        buildUsageUniqueIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "uq_transaction_limit_usage_bucket"
                );
                executeIndexStatement(
                        statement,
                        buildUsageAccountIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_transaction_limit_usage_account_period"
                );
                executeIndexStatement(
                        statement,
                        buildUsageSubjectIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_transaction_limit_usage_subject"
                );
                executeIndexStatement(
                        statement,
                        buildProfileTagForeignKeyIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_tlp_tag_id"
                );
                executeIndexStatement(
                        statement,
                        buildDetailLimitForeignKeyIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_tlpd_limit_id"
                );
                executeIndexStatement(
                        statement,
                        buildPeriodLimitDetailsForeignKeyIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_tlpp_limit_details_id"
                );
                executeIndexStatement(
                        statement,
                        buildUsageLimitForeignKeyIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_tlu_limit_id"
                );
                executeIndexStatement(
                        statement,
                        buildUsageLimitDetailsForeignKeyIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_tlu_limit_details_id"
                );
                executeIndexStatement(
                        statement,
                        buildUsageTagForeignKeyIndexSql(normalizedSchemaName),
                        normalizedSchemaName,
                        "idx_tlu_tag_id"
                );
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize transaction limit tables for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured transaction limit tables exist in schema {}", normalizedSchemaName);
    }

    private void executeTableStatement(
            Connection connection,
            Statement statement,
            String sql,
            String schemaName,
            String tableName
    ) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException ex) {
            if (isPostgresOwnershipOrPrivilegeError(ex) && tableExists(connection, schemaName, tableName)) {
                log.warn(
                        "Skipping transaction limit table {}.{} because it already exists and the current database user cannot modify its definition",
                        schemaName,
                        tableName
                );
                return;
            }
            throw ex;
        }
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
                        "Skipping transaction limit index {} in schema {} because the current database user is not the table owner",
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

    private boolean tableExists(Connection connection, String schemaName, String tableName) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, schemaName + "." + tableName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
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

    private String buildProfileTableSql(String schemaName) {
        return """
                CREATE TABLE IF NOT EXISTS %s.transaction_limit_profile (
                    limit_id BIGSERIAL PRIMARY KEY,
                    limit_name VARCHAR(150) NOT NULL,
                    tag_id BIGINT NOT NULL,
                    limit_type VARCHAR(20) NOT NULL,
                    subject_key VARCHAR(50) NOT NULL,
                    details JSONB,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    wallet_type VARCHAR(50) NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    min_residual_balance NUMERIC(19, 0),
                    max_balance NUMERIC(19, 0),
                    created_by VARCHAR(100),
                    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    modified_by VARCHAR(100),
                    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    version BIGINT
                )
                """.formatted(schemaName);
    }

    private String buildDetailTableSql(String schemaName) {
        return """
                CREATE TABLE IF NOT EXISTS %s.transaction_limit_profile_details (
                    limit_details_id BIGSERIAL PRIMARY KEY,
                    limit_id BIGINT NOT NULL,
                    party_type VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    operation_type VARCHAR(50) NOT NULL DEFAULT 'ALL',
                    request_gateway VARCHAR(50) NOT NULL DEFAULT 'ALL',
                    min_txn_amount NUMERIC(19, 0),
                    max_txn_amount NUMERIC(19, 0),
                    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    version BIGINT
                )
                """.formatted(schemaName);
    }

    private String buildPeriodTableSql(String schemaName) {
        return """
                CREATE TABLE IF NOT EXISTS %s.transaction_limit_profile_period (
                    limit_period_id BIGSERIAL PRIMARY KEY,
                    limit_details_id BIGINT NOT NULL,
                    period_type VARCHAR(20) NOT NULL,
                    max_count INTEGER,
                    max_amount NUMERIC(19, 0),
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
                    version BIGINT
                )
                """.formatted(schemaName);
    }

    private String buildUsageTableSql(String schemaName) {
        return """
                CREATE TABLE IF NOT EXISTS %s.transaction_limit_usage (
                    usage_id BIGSERIAL PRIMARY KEY,
                    subject_key VARCHAR(50) NOT NULL,
                    subject_value VARCHAR(200) NOT NULL,
                    account_id VARCHAR(100),
                    limit_id BIGINT NOT NULL,
                    limit_details_id BIGINT NOT NULL,
                    tag_id BIGINT NOT NULL,
                    period_type VARCHAR(20) NOT NULL,
                    operation_type VARCHAR(50) NOT NULL,
                    request_gateway VARCHAR(50) NOT NULL,
                    payer_count INTEGER NOT NULL DEFAULT 0,
                    payer_amount NUMERIC(19, 0) NOT NULL DEFAULT 0,
                    payee_count INTEGER NOT NULL DEFAULT 0,
                    payee_amount NUMERIC(19, 0) NOT NULL DEFAULT 0,
                    last_transaction_id VARCHAR(30),
                    last_transaction_date TIMESTAMP
                )
                """.formatted(schemaName);
    }

    private String buildProfileMigrationSql(String schemaName) {
        return """
                ALTER TABLE %s.transaction_limit_profile
                    DROP COLUMN IF EXISTS priority
                """.formatted(schemaName);
    }

    private String buildUsageMigrationSql(String schemaName) {
        return """
                ALTER TABLE %s.transaction_limit_usage
                    ADD COLUMN IF NOT EXISTS subject_value VARCHAR(200),
                    ALTER COLUMN account_id DROP NOT NULL,
                    DROP COLUMN IF EXISTS subject_value_hash,
                    DROP COLUMN IF EXISTS subject_value_masked,
                    DROP COLUMN IF EXISTS limit_period_id,
                    DROP COLUMN IF EXISTS wallet_type,
                    DROP COLUMN IF EXISTS currency,
                    DROP COLUMN IF EXISTS period_start,
                    DROP COLUMN IF EXISTS period_end,
                    DROP COLUMN IF EXISTS status,
                    DROP COLUMN IF EXISTS created_on,
                    DROP COLUMN IF EXISTS modified_on,
                    DROP COLUMN IF EXISTS version
                """.formatted(schemaName);
    }

    private String buildProfileStoredAmountScaleMigrationSql(String schemaName) {
        return """
                ALTER TABLE %s.transaction_limit_profile
                    ALTER COLUMN min_residual_balance TYPE NUMERIC(19, 0) USING min_residual_balance::NUMERIC(19, 0),
                    ALTER COLUMN max_balance TYPE NUMERIC(19, 0) USING max_balance::NUMERIC(19, 0)
                """.formatted(schemaName);
    }

    private String buildDetailStoredAmountScaleMigrationSql(String schemaName) {
        return """
                ALTER TABLE %s.transaction_limit_profile_details
                    ALTER COLUMN min_txn_amount TYPE NUMERIC(19, 0) USING min_txn_amount::NUMERIC(19, 0),
                    ALTER COLUMN max_txn_amount TYPE NUMERIC(19, 0) USING max_txn_amount::NUMERIC(19, 0)
                """.formatted(schemaName);
    }

    private String buildPeriodStoredAmountScaleMigrationSql(String schemaName) {
        return """
                ALTER TABLE %s.transaction_limit_profile_period
                    ALTER COLUMN max_amount TYPE NUMERIC(19, 0) USING max_amount::NUMERIC(19, 0)
                """.formatted(schemaName);
    }

    private String buildUsageStoredAmountScaleMigrationSql(String schemaName) {
        return """
                ALTER TABLE %s.transaction_limit_usage
                    ALTER COLUMN payer_amount TYPE NUMERIC(19, 0) USING payer_amount::NUMERIC(19, 0),
                    ALTER COLUMN payee_amount TYPE NUMERIC(19, 0) USING payee_amount::NUMERIC(19, 0)
                """.formatted(schemaName);
    }

    private String buildProfileTypeIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_type
                    ON %s.transaction_limit_profile(limit_type, status, wallet_type, currency)
                """.formatted(schemaName);
    }

    private String buildProfileTagIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_tag
                    ON %s.transaction_limit_profile(tag_id, status, created_on DESC)
                """.formatted(schemaName);
    }

    private String buildDetailProfileIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_transaction_limit_details_profile
                    ON %s.transaction_limit_profile_details(limit_id, party_type, operation_type, request_gateway, status)
                """.formatted(schemaName);
    }

    private String buildPeriodDetailIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_transaction_limit_period_details
                    ON %s.transaction_limit_profile_period(limit_details_id, period_type, status)
                """.formatted(schemaName);
    }

    private String buildUsageUniqueIndexSql(String schemaName) {
        return """
                CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_limit_usage_bucket
                    ON %s.transaction_limit_usage(
                        subject_key,
                        subject_value,
                        limit_id,
                        limit_details_id,
                        period_type,
                        operation_type,
                        request_gateway
                    )
                """.formatted(schemaName);
    }

    private String buildUsageAccountIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_transaction_limit_usage_account_period
                    ON %s.transaction_limit_usage(account_id, period_type, last_transaction_date DESC)
                """.formatted(schemaName);
    }

    private String buildUsageSubjectIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_transaction_limit_usage_subject
                    ON %s.transaction_limit_usage(subject_key, subject_value, last_transaction_date DESC)
                """.formatted(schemaName);
    }

    private String buildProfileTagForeignKeyIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_tlp_tag_id
                    ON %s.transaction_limit_profile(tag_id)
                """.formatted(schemaName);
    }

    private String buildDetailLimitForeignKeyIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_tlpd_limit_id
                    ON %s.transaction_limit_profile_details(limit_id)
                """.formatted(schemaName);
    }

    private String buildPeriodLimitDetailsForeignKeyIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_tlpp_limit_details_id
                    ON %s.transaction_limit_profile_period(limit_details_id)
                """.formatted(schemaName);
    }

    private String buildUsageLimitForeignKeyIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_tlu_limit_id
                    ON %s.transaction_limit_usage(limit_id)
                """.formatted(schemaName);
    }

    private String buildUsageLimitDetailsForeignKeyIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_tlu_limit_details_id
                    ON %s.transaction_limit_usage(limit_details_id)
                """.formatted(schemaName);
    }

    private String buildUsageTagForeignKeyIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_tlu_tag_id
                    ON %s.transaction_limit_usage(tag_id)
                """.formatted(schemaName);
    }
}
