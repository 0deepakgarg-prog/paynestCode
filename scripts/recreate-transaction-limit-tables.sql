BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM tenant_movii.tags
        WHERE tag_code = 'PREMIUMCUSTOMER'
           OR tag_name = 'PREMIUMCUSTOMER'
    ) THEN
        RAISE EXCEPTION 'PREMIUMCUSTOMER tag not found in tenant_movii.tags';
    END IF;
END $$;

DROP TABLE IF EXISTS tenant_movii.transaction_limit_usage;
DROP TABLE IF EXISTS tenant_movii.transaction_limit_profile_period;
DROP TABLE IF EXISTS tenant_movii.transaction_limit_profile_details;
DROP TABLE IF EXISTS tenant_movii.transaction_limit_profile;

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
    min_residual_balance NUMERIC(19,0),
    max_balance NUMERIC(19,0),
    created_by VARCHAR(100),
    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
    modified_by VARCHAR(100),
    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT,
    CONSTRAINT fk_transaction_limit_profile_tag
        FOREIGN KEY (tag_id)
        REFERENCES tenant_movii.tags(tag_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE tenant_movii.transaction_limit_profile
TO paynest_app;

GRANT USAGE, SELECT
ON SEQUENCE tenant_movii.transaction_limit_profile_limit_id_seq
TO paynest_app;

CREATE TABLE IF NOT EXISTS tenant_movii.transaction_limit_profile_details (
    limit_details_id BIGSERIAL PRIMARY KEY,
    limit_id BIGINT NOT NULL,
    party_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    operation_type VARCHAR(50) NOT NULL DEFAULT 'ALL',
    request_gateway VARCHAR(50) NOT NULL DEFAULT 'ALL',
    min_txn_amount NUMERIC(19,0),
    max_txn_amount NUMERIC(19,0),
    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT,
    CONSTRAINT fk_transaction_limit_details_profile
        FOREIGN KEY (limit_id)
        REFERENCES tenant_movii.transaction_limit_profile(limit_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE tenant_movii.transaction_limit_profile_details
TO paynest_app;

GRANT USAGE, SELECT
ON SEQUENCE tenant_movii.transaction_limit_profile_details_limit_details_id_seq
TO paynest_app;

CREATE TABLE IF NOT EXISTS tenant_movii.transaction_limit_profile_period (
    limit_period_id BIGSERIAL PRIMARY KEY,
    limit_details_id BIGINT NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    max_count INTEGER,
    max_amount NUMERIC(19,0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
    modified_on TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT,
    CONSTRAINT fk_transaction_limit_period_details
        FOREIGN KEY (limit_details_id)
        REFERENCES tenant_movii.transaction_limit_profile_details(limit_details_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE tenant_movii.transaction_limit_profile_period
TO paynest_app;

GRANT USAGE, SELECT
ON SEQUENCE tenant_movii.transaction_limit_profile_period_limit_period_id_seq
TO paynest_app;

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
    payer_amount NUMERIC(19,0) NOT NULL DEFAULT 0,
    payee_count INTEGER NOT NULL DEFAULT 0,
    payee_amount NUMERIC(19,0) NOT NULL DEFAULT 0,
    last_transaction_id VARCHAR(30),
    last_transaction_date TIMESTAMP,
    CONSTRAINT fk_transaction_limit_usage_profile
        FOREIGN KEY (limit_id)
        REFERENCES tenant_movii.transaction_limit_profile(limit_id),
    CONSTRAINT fk_transaction_limit_usage_details
        FOREIGN KEY (limit_details_id)
        REFERENCES tenant_movii.transaction_limit_profile_details(limit_details_id),
    CONSTRAINT fk_transaction_limit_usage_tag
        FOREIGN KEY (tag_id)
        REFERENCES tenant_movii.tags(tag_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE tenant_movii.transaction_limit_usage
TO paynest_app;

GRANT USAGE, SELECT
ON SEQUENCE tenant_movii.transaction_limit_usage_usage_id_seq
TO paynest_app;

CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_type
    ON tenant_movii.transaction_limit_profile
    (limit_type, status, wallet_type, currency);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_profile_tag
    ON tenant_movii.transaction_limit_profile
    (tag_id, status, created_on DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_details_profile
    ON tenant_movii.transaction_limit_profile_details
    (limit_id, party_type, operation_type, request_gateway, status);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_period_details
    ON tenant_movii.transaction_limit_profile_period
    (limit_details_id, period_type, status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_limit_usage_bucket
    ON tenant_movii.transaction_limit_usage
    (
        subject_key,
        subject_value,
        limit_id,
        limit_details_id,
        period_type,
        operation_type,
        request_gateway
    );

CREATE INDEX IF NOT EXISTS idx_transaction_limit_usage_account_period
    ON tenant_movii.transaction_limit_usage
    (account_id, period_type, last_transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_limit_usage_subject
    ON tenant_movii.transaction_limit_usage
    (subject_key, subject_value, last_transaction_date DESC);

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

INSERT INTO tenant_movii.error_catalog
(
    error_code,
    language_code,
    message_template,
    http_status,
    category,
    module,
    is_active
)
VALUES
('LIMIT_TAG_NOT_FOUND', 'en', 'No active limit tag found for {partyType} account', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_SUBJECT_KEY_MISSING', 'en', 'Limit subject key is not configured for limit profile {limitId}', 400, 'CONFIGURATION', 'LIMIT', TRUE),
('LIMIT_SUBJECT_VALUE_NOT_FOUND', 'en', 'Required limit subject value {subjectKey} was not found for {partyType} account', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_PROFILE_NOT_FOUND', 'en', 'No active transaction limit profile found for {partyType} account', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_PROFILE_DETAILS_NOT_FOUND', 'en', 'No active transaction limit details found for {partyType} account', 400, 'CONFIGURATION', 'LIMIT', TRUE),
('LIMIT_PERIOD_NOT_CONFIGURED', 'en', 'No active {periodType} limit period configured for limit details {limitDetailsId}', 400, 'CONFIGURATION', 'LIMIT', TRUE),
('LIMIT_MIN_TRANSACTION_AMOUNT_NOT_MET', 'en', 'Transaction amount is below the minimum allowed amount', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_MAX_TRANSACTION_AMOUNT_EXCEEDED', 'en', 'Transaction amount exceeds the maximum allowed amount', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_DAILY_COUNT_EXCEEDED', 'en', 'Daily transaction count limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_DAILY_AMOUNT_EXCEEDED', 'en', 'Daily transaction amount limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_MONTHLY_COUNT_EXCEEDED', 'en', 'Monthly transaction count limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_MONTHLY_AMOUNT_EXCEEDED', 'en', 'Monthly transaction amount limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_MIN_RESIDUAL_BALANCE_NOT_MET', 'en', 'Wallet balance after transaction would be below minimum residual balance', 400, 'BUSINESS', 'LIMIT', TRUE),
('LIMIT_MAX_BALANCE_EXCEEDED', 'en', 'Wallet balance after transaction would exceed maximum allowed balance', 400, 'BUSINESS', 'LIMIT', TRUE)
ON CONFLICT (error_code, language_code)
DO UPDATE
SET
    message_template = EXCLUDED.message_template,
    http_status = EXCLUDED.http_status,
    category = EXCLUDED.category,
    module = EXCLUDED.module,
    is_active = EXCLUDED.is_active;

WITH tag_ref AS (
    SELECT tag_id
    FROM tenant_movii.tags
    WHERE tag_code = 'PREMIUMCUSTOMER'
       OR tag_name = 'PREMIUMCUSTOMER'
    ORDER BY tag_id
    LIMIT 1
),
new_profile AS (
    INSERT INTO tenant_movii.transaction_limit_profile (
        limit_name,
        tag_id,
        limit_type,
        subject_key,
        details,
        status,
        wallet_type,
        currency,
        min_residual_balance,
        max_balance,
        created_by,
        modified_by
    )
    SELECT
        'Premium Customer MAIN USD Global Limit',
        tag_id,
        'GLOBAL',
        'ACCOUNT_ID',
        '{"description":"Premium customer global transaction limit","amountStorage":"currency-factor"}'::jsonb,
        'ACTIVE',
        'MAIN',
        'USD',
        0,
        50000000,
        'SYSTEM',
        'SYSTEM'
    FROM tag_ref
    RETURNING limit_id
),
new_details AS (
    INSERT INTO tenant_movii.transaction_limit_profile_details (
        limit_id,
        party_type,
        status,
        operation_type,
        request_gateway,
        min_txn_amount,
        max_txn_amount
    )
    SELECT
        limit_id,
        party_type,
        'ACTIVE',
        'ALL',
        'ALL',
        100,
        10000000
    FROM new_profile
    CROSS JOIN (VALUES ('DEBITOR'), ('CREDITOR')) AS d(party_type)
    RETURNING limit_details_id
)
INSERT INTO tenant_movii.transaction_limit_profile_period (
    limit_details_id,
    period_type,
    max_count,
    max_amount,
    status
)
SELECT
    limit_details_id,
    period_type,
    max_count,
    max_amount,
    'ACTIVE'
FROM new_details
CROSS JOIN (
    VALUES
        ('DAILY', 20, 10000000),
        ('MONTHLY', 200, 100000000)
) AS p(period_type, max_count, max_amount);

COMMIT;
