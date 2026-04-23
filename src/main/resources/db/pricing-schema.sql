CREATE TABLE tenant_movii.pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    pricing_name VARCHAR(100) NOT NULL,
    service_code VARCHAR(50) NOT NULL,
    rule_type VARCHAR(30) NOT NULL CHECK (
        rule_type IN ('SERVICE_CHARGE', 'COMMISSION', 'DISCOUNT', 'CASHBACK')
    ),
    pricing_type VARCHAR(20),
    payer VARCHAR(20) NOT NULL CHECK (
        payer IN ('SENDER', 'RECEIVER', 'SYSTEM', 'SPLIT')
    ),
    pay_by VARCHAR(20),
    payer_split JSONB,
    sender_tag_key VARCHAR(255) NOT NULL,
    receiver_tag_key VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    pricing_config JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
