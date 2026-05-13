CREATE TABLE IF NOT EXISTS tenant_movii.cashback_payout (
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
);

CREATE INDEX IF NOT EXISTS idx_cashback_payout_due
    ON tenant_movii.cashback_payout(status, pay_at);
