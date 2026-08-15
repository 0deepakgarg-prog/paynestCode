CREATE TABLE tenant_movii.card_pre_auth_hold
(
    hold_id            VARCHAR(50) PRIMARY KEY,
    cms_transaction_id VARCHAR(100)   NOT NULL UNIQUE,
    wallet_id          BIGINT         NOT NULL,
    account_id         VARCHAR(30)    NOT NULL,
    currency           VARCHAR(10)    NOT NULL,
    wallet_type        VARCHAR(50)    NOT NULL,
    original_amount    NUMERIC(19, 2) NOT NULL,
    hold_amount        NUMERIC(19, 2) NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    cms_reference      VARCHAR(100),
    merchant_id        VARCHAR(100),
    comments           VARCHAR(300),
    additional_info    TEXT,
    created_on         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_on        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_card_pre_auth_hold_cms_txn
    ON tenant_movii.card_pre_auth_hold (cms_transaction_id);

CREATE INDEX IF NOT EXISTS idx_card_pre_auth_hold_wallet_status
    ON tenant_movii.card_pre_auth_hold (wallet_id, status);
