CREATE TABLE IF NOT EXISTS tenant_movii.passcode (
    passcode_id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(30) NOT NULL,
    cashout_transaction_id VARCHAR(30),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    unregistered_msisdn VARCHAR(30) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    kyc_document_id VARCHAR(100),
    sender_msisdn VARCHAR(30),
    sender_account_id VARCHAR(30) NOT NULL,
    passcode VARCHAR(10) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_on TIMESTAMP NOT NULL,
    modified_on TIMESTAMP NOT NULL,
    redeemed_on TIMESTAMP,
    field1 VARCHAR(250),
    field2 VARCHAR(250),
    field3 VARCHAR(250),
    field4 VARCHAR(250),
    field5 VARCHAR(250),
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_passcode_lookup
    ON tenant_movii.passcode(passcode, unregistered_msisdn, status);
