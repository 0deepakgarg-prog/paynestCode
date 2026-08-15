CREATE TABLE IF NOT EXISTS tenant_movii.batches (
    batch_id VARCHAR(30) PRIMARY KEY,
    batch_reference VARCHAR(100),
    batch_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    transaction_id VARCHAR(30),
    total_records INTEGER NOT NULL DEFAULT 0,
    valid_records INTEGER NOT NULL DEFAULT 0,
    failed_records INTEGER NOT NULL DEFAULT 0,
    total_amount NUMERIC(19, 0) NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL,
    debitor_account_id VARCHAR(30),
    debitor_wallet_type VARCHAR(50),
    debitor_currency VARCHAR(10),
    created_by VARCHAR(30),
    approved_by VARCHAR(30),
    rejected_by VARCHAR(30),
    validation_started_on TIMESTAMP,
    validation_completed_on TIMESTAMP,
    approved_on TIMESTAMP,
    rejected_on TIMESTAMP,
    processing_started_on TIMESTAMP,
    processing_completed_on TIMESTAMP,
    failure_reason VARCHAR(500),
    remarks VARCHAR(500),
    additional_info JSONB,
    created_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(30) NOT NULL,
    modified_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_batches_status CHECK (
        status IN (
            'VALIDATION_INITIATED',
            'VALIDATION_IN_PROGRESS',
            'PENDING_APPROVAL',
            'APPROVED',
            'REJECTED',
            'PROCESSING',
            'SUCCESS',
            'PARTIAL_SUCCESS',
            'FAILED',
            'REFUNDED'
        )
    )
);

CREATE TABLE IF NOT EXISTS tenant_movii.batch_details (
    batch_detail_id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(30) NOT NULL,
    item_reference VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    transaction_id VARCHAR(30),
    amount NUMERIC(19, 0) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    creditor_wallet_type VARCHAR(50),
    creditor_currency VARCHAR(10),
    creditor_identifier_type VARCHAR(30),
    creditor_identifier_value VARCHAR(50),
    payment_reference VARCHAR(100),
    comments VARCHAR(300),
    validation_error_code VARCHAR(100),
    validation_error_message VARCHAR(500),
    processing_error_code VARCHAR(100),
    processing_error_message VARCHAR(500),
    additional_info JSONB,
    CONSTRAINT fk_batch_details_batch
        FOREIGN KEY (batch_id)
        REFERENCES tenant_movii.batches (batch_id),
    CONSTRAINT chk_batch_details_status CHECK (
        status IN (
            'PENDING',
            'VALIDATION_IN_PROGRESS',
            'VALIDATED',
            'VALIDATION_FAILED',
            'PROCESSING',
            'SUCCESS',
            'FAILED',
            'SKIPPED'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_batches_status
    ON tenant_movii.batches (status);

CREATE INDEX IF NOT EXISTS idx_batches_reference
    ON tenant_movii.batches (batch_reference);

CREATE INDEX IF NOT EXISTS idx_batches_created_on
    ON tenant_movii.batches (created_on);

CREATE INDEX IF NOT EXISTS idx_batch_details_batch_id
    ON tenant_movii.batch_details (batch_id);

CREATE INDEX IF NOT EXISTS idx_batch_details_status
    ON tenant_movii.batch_details (status);

CREATE INDEX IF NOT EXISTS idx_batch_details_transaction_id
    ON tenant_movii.batch_details (transaction_id);

CREATE INDEX IF NOT EXISTS idx_batch_details_item_reference
    ON tenant_movii.batch_details (item_reference);
