CREATE TABLE IF NOT EXISTS tenant_movii.transaction_limit_profile (
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
    version BIGINT,
    CONSTRAINT fk_transaction_limit_profile_tag
        FOREIGN KEY (tag_id) REFERENCES tenant_movii.tags(tag_id)
);

CREATE TABLE IF NOT EXISTS tenant_movii.transaction_limit_profile_details (
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
    version BIGINT,
    CONSTRAINT fk_transaction_limit_details_profile
        FOREIGN KEY (limit_id) REFERENCES tenant_movii.transaction_limit_profile(limit_id)
);

CREATE TABLE IF NOT EXISTS tenant_movii.transaction_limit_profile_period (
    limit_period_id BIGSERIAL PRIMARY KEY,
    limit_details_id BIGINT NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    max_count INTEGER,
    max_amount NUMERIC(19, 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT,
    CONSTRAINT fk_transaction_limit_period_details
        FOREIGN KEY (limit_details_id)
        REFERENCES tenant_movii.transaction_limit_profile_details(limit_details_id)
);

CREATE TABLE IF NOT EXISTS tenant_movii.transaction_limit_usage (
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
    last_transaction_date TIMESTAMP,
    CONSTRAINT fk_transaction_limit_usage_profile
        FOREIGN KEY (limit_id) REFERENCES tenant_movii.transaction_limit_profile(limit_id),
    CONSTRAINT fk_transaction_limit_usage_details
        FOREIGN KEY (limit_details_id)
        REFERENCES tenant_movii.transaction_limit_profile_details(limit_details_id),
    CONSTRAINT fk_transaction_limit_usage_tag
        FOREIGN KEY (tag_id) REFERENCES tenant_movii.tags(tag_id)
);

ALTER TABLE tenant_movii.transaction_limit_profile
    ALTER COLUMN min_residual_balance TYPE NUMERIC(19, 0) USING min_residual_balance::NUMERIC(19, 0),
    ALTER COLUMN max_balance TYPE NUMERIC(19, 0) USING max_balance::NUMERIC(19, 0);

ALTER TABLE tenant_movii.transaction_limit_profile_details
    ALTER COLUMN min_txn_amount TYPE NUMERIC(19, 0) USING min_txn_amount::NUMERIC(19, 0),
    ALTER COLUMN max_txn_amount TYPE NUMERIC(19, 0) USING max_txn_amount::NUMERIC(19, 0);

ALTER TABLE tenant_movii.transaction_limit_profile_period
    ALTER COLUMN max_amount TYPE NUMERIC(19, 0) USING max_amount::NUMERIC(19, 0);

ALTER TABLE tenant_movii.transaction_limit_usage
    ALTER COLUMN payer_amount TYPE NUMERIC(19, 0) USING payer_amount::NUMERIC(19, 0),
    ALTER COLUMN payee_amount TYPE NUMERIC(19, 0) USING payee_amount::NUMERIC(19, 0);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_type
    ON tenant_movii.transaction_limit_profile(limit_type, status, wallet_type, currency);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_tag
    ON tenant_movii.transaction_limit_profile(tag_id, status, created_on DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_details_profile
    ON tenant_movii.transaction_limit_profile_details(limit_id, party_type, operation_type, request_gateway, status);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_period_details
    ON tenant_movii.transaction_limit_profile_period(limit_details_id, period_type, status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_limit_usage_bucket
    ON tenant_movii.transaction_limit_usage(
        subject_key,
        subject_value,
        limit_id,
        limit_details_id,
        period_type,
        operation_type,
        request_gateway
    );

CREATE INDEX IF NOT EXISTS idx_transaction_limit_usage_account_period
    ON tenant_movii.transaction_limit_usage(account_id, period_type, last_transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_usage_subject
    ON tenant_movii.transaction_limit_usage(subject_key, subject_value, last_transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_tlp_tag_id
    ON tenant_movii.transaction_limit_profile(tag_id);

CREATE INDEX IF NOT EXISTS idx_tlpd_limit_id
    ON tenant_movii.transaction_limit_profile_details(limit_id);

CREATE INDEX IF NOT EXISTS idx_tlpp_limit_details_id
    ON tenant_movii.transaction_limit_profile_period(limit_details_id);

CREATE INDEX IF NOT EXISTS idx_tlu_limit_id
    ON tenant_movii.transaction_limit_usage(limit_id);

CREATE INDEX IF NOT EXISTS idx_tlu_limit_details_id
    ON tenant_movii.transaction_limit_usage(limit_details_id);

CREATE INDEX IF NOT EXISTS idx_tlu_tag_id
    ON tenant_movii.transaction_limit_usage(tag_id);
