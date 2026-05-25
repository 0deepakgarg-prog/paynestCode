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
public class CashbackPayoutSchemaInitializer {

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
            try {
                statement.execute(buildIndexSql(normalizedSchemaName));
            } catch (SQLException ex) {
                if (isInsufficientPrivilege(ex)) {
                    log.warn(
                            "Skipping cashback_payout index creation for schema {} because the current database user does not own the existing table",
                            normalizedSchemaName
                    );
                } else {
                    throw ex;
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to initialize cashback_payout table for schema " + normalizedSchemaName,
                    ex
            );
        }

        log.info("Ensured cashback_payout table exists in schema {}", normalizedSchemaName);
    }

    private boolean isInsufficientPrivilege(SQLException ex) {
        return "42501".equals(ex.getSQLState());
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
                CREATE TABLE IF NOT EXISTS %s.cashback_payout (
                    cashback_payout_id BIGSERIAL PRIMARY KEY,
                    original_transaction_id VARCHAR(30) NOT NULL,
                    payout_transaction_id VARCHAR(30),
                    service_code VARCHAR(15) NOT NULL,
                    beneficiary_account_id VARCHAR(30) NOT NULL,
                    beneficiary_party VARCHAR(20),
                    amount NUMERIC(19, 4) NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    payment_schedule VARCHAR(30) NOT NULL,
                    pay_at TIMESTAMP NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    pricing_rule_details VARCHAR(4000),
                    failure_reason VARCHAR(300),
                    created_on TIMESTAMP NOT NULL,
                    modified_on TIMESTAMP NOT NULL,
                    version BIGINT
                )
                """.formatted(schemaName);
    }

    private String buildIndexSql(String schemaName) {
        return """
                CREATE INDEX IF NOT EXISTS idx_cashback_payout_due
                    ON %s.cashback_payout(status, pay_at)
                """.formatted(schemaName);
    }
}
