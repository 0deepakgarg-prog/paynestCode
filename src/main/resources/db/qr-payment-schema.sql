ALTER TABLE tenant_movii.transactions
    ADD COLUMN IF NOT EXISTS debitor_wallet_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS debitor_currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS creditor_wallet_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS creditor_currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS payment_via_qr BOOLEAN NOT NULL DEFAULT FALSE,
    ALTER COLUMN transaction_value TYPE NUMERIC(19, 0) USING transaction_value::NUMERIC(19, 0);

ALTER TABLE tenant_movii.transaction_details
    ADD COLUMN IF NOT EXISTS wallet_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS attr_1_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_1_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_2_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_2_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_3_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_3_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_4_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_4_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_5_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_5_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_6_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attr_6_value VARCHAR(255),
    ALTER COLUMN txn_sequence_number TYPE BIGINT USING txn_sequence_number::BIGINT,
    ALTER COLUMN second_identifier_id TYPE VARCHAR(80),
    ALTER COLUMN transaction_value TYPE NUMERIC(19, 0) USING transaction_value::NUMERIC(19, 0),
    ALTER COLUMN approved_value TYPE NUMERIC(19, 0) USING approved_value::NUMERIC(19, 0),
    ALTER COLUMN previous_balance TYPE NUMERIC(19, 0) USING previous_balance::NUMERIC(19, 0),
    ALTER COLUMN post_balance TYPE NUMERIC(19, 0) USING post_balance::NUMERIC(19, 0),
    ALTER COLUMN previous_fic_balance TYPE NUMERIC(19, 0) USING previous_fic_balance::NUMERIC(19, 0),
    ALTER COLUMN post_fic_balance TYPE NUMERIC(19, 0) USING post_fic_balance::NUMERIC(19, 0),
    ALTER COLUMN previous_frozen_balance TYPE NUMERIC(19, 0) USING previous_frozen_balance::NUMERIC(19, 0),
    ALTER COLUMN post_frozen_balance TYPE NUMERIC(19, 0) USING post_frozen_balance::NUMERIC(19, 0);

CREATE TABLE IF NOT EXISTS tenant_movii.qr_payment_intent (
    qr_intent_id VARCHAR(40) PRIMARY KEY,
    operation_type VARCHAR(20) NOT NULL,
    creditor_identifier_type VARCHAR(30) NOT NULL,
    creditor_identifier_value VARCHAR(30) NOT NULL,
    creditor_account_type VARCHAR(30) NOT NULL,
    creditor_wallet_type VARCHAR(50) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2),
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    transaction_id VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_qr_payment_intent_status_expiry
    ON tenant_movii.qr_payment_intent (status, expires_at);
