CREATE TABLE IF NOT EXISTS tenant_movii.recent_recipients (
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
);

CREATE INDEX IF NOT EXISTS idx_recent_recipients_account_last_paid
    ON tenant_movii.recent_recipients(account_id, last_paid_at DESC);

CREATE INDEX IF NOT EXISTS idx_recent_recipients_account_service_last_paid
    ON tenant_movii.recent_recipients(account_id, service_code, last_paid_at DESC);
