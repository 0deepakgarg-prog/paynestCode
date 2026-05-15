ALTER TABLE tenant_movii.transactions
    ADD COLUMN IF NOT EXISTS payment_via_qr BOOLEAN NOT NULL DEFAULT FALSE;

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
