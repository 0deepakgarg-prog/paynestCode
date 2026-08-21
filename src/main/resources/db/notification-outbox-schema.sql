CREATE TABLE IF NOT EXISTS tenant_movii.notification_outbox (
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
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending
    ON tenant_movii.notification_outbox(status, next_attempt_at, created_on);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_transaction
    ON tenant_movii.notification_outbox(transaction_id);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_channel_status
    ON tenant_movii.notification_outbox(channel, status, created_on);
