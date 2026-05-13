param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$Database = "postgres",
    [string]$DbLoginUser = "postgres",
    [string]$DatabasePwd = "postgres",
    [string]$DbUser = "paynest_app",
    [string]$DbPassword = "paynest_app",
    [string]$TenantId = "e2e",
    [string]$TenantName = "Default E2E Test Tenant",
    [string]$TenantSchema = "tenant_$TenantId",
    [string]$TenantTimeZone = "UTC",
    [string]$DefaultAdminAccountId = "ADMIN0000000001",
    [string]$DefaultAdminLoginId = "superadmin",
    [string]$DefaultAdminPassword = "Admin@123",
    [string]$DefaultAdminEmail = "superadmin@paynest.local",
    [string]$DefaultAdminMobile = "+10000000000",
    [string]$DefaultAdminAuthSalt = "paynest-default-admin-salt"
)

$ErrorActionPreference = "Stop"

function Write-DbBootstrapLog {
    param(
        [string]$Message
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
    Write-Host "[$timestamp] [db-bootstrap] $Message"
}

function Invoke-PsqlCommand {
    param(
        [string[]]$Arguments,
        [string]$StepName
    )

    Write-DbBootstrapLog "Starting psql step '$StepName'. arguments=$($Arguments -join ' ')"
    $startedAt = Get-Date
    & psql @Arguments
    $exitCode = $LASTEXITCODE
    $durationMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
    Write-DbBootstrapLog "Finished psql step '$StepName'. exitCode=$exitCode durationMs=$durationMs"

    if ($exitCode -ne 0) {
        throw "psql step '$StepName' exited with code $exitCode"
    }
}

Write-DbBootstrapLog "Bootstrap parameters: dbHost=$DbHost dbPort=$DbPort database=$Database dbLoginUser=$DbLoginUser dbUser=$DbUser tenantId=$TenantId tenantSchema=$TenantSchema tenantName='$TenantName' tenantTimeZone=$TenantTimeZone"

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found on PATH. Install PostgreSQL client tools or add psql.exe to PATH."
}

$psqlCommand = Get-Command psql
Write-DbBootstrapLog "Found psql. path=$($psqlCommand.Source)"

$env:PGPASSWORD = $DatabasePwd
$sqlFile = Join-Path ([System.IO.Path]::GetTempPath()) ("paynest-bootstrap-{0}.sql" -f ([guid]::NewGuid()))
Write-DbBootstrapLog "Generated temporary SQL file path. sqlFile=$sqlFile"
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$adminCredentialBytes = [System.Text.Encoding]::UTF8.GetBytes($DefaultAdminPassword + $DefaultAdminAuthSalt)
$DefaultAdminPasswordHash = (($sha256.ComputeHash($adminCredentialBytes) | ForEach-Object { $_.ToString("x2") }) -join "")

$sql = @"
BEGIN;

CREATE SCHEMA IF NOT EXISTS $TenantSchema;

CREATE TABLE IF NOT EXISTS public.tenant_registry (
    tenant_id VARCHAR(50) PRIMARY KEY,
    tenant_name VARCHAR(100),
    schema_name VARCHAR(100) NOT NULL,
    iana_time_zone VARCHAR(100),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO public.tenant_registry (
    tenant_id,
    tenant_name,
    schema_name,
    iana_time_zone,
    status,
    created_at,
    updated_at
)
VALUES (
    '$TenantId',
    '$TenantName',
    '$TenantSchema',
    '$TenantTimeZone',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id) DO UPDATE
SET tenant_name = EXCLUDED.tenant_name,
    schema_name = EXCLUDED.schema_name,
    iana_time_zone = EXCLUDED.iana_time_zone,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS $TenantSchema.enumerations (
    id BIGSERIAL PRIMARY KEY,
    enum_type VARCHAR(50) NOT NULL,
    enum_code VARCHAR(50) NOT NULL,
    enum_value VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    is_system BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_enumerations_type_code UNIQUE (enum_type, enum_code)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.supported_languages (
    id BIGSERIAL PRIMARY KEY,
    language_code VARCHAR(10) NOT NULL UNIQUE,
    language_name VARCHAR(100) NOT NULL,
    display_order INTEGER DEFAULT 0,
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE $TenantSchema.supported_languages
    ADD COLUMN IF NOT EXISTS display_order INTEGER DEFAULT 0;

CREATE TABLE IF NOT EXISTS $TenantSchema.account (
    account_id TEXT PRIMARY KEY,
    account_type VARCHAR(50) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    mobile_number VARCHAR(50),
    email VARCHAR(255),
    address TEXT,
    gender VARCHAR(50),
    date_of_birth DATE,
    preferred_lang VARCHAR(20),
    nationality VARCHAR(100),
    ssn VARCHAR(100),
    remarks TEXT,
    attr1 TEXT,
    attr2 TEXT,
    attr3 TEXT,
    attr4 TEXT,
    attr5 TEXT,
    attr6 TEXT,
    attr7 TEXT,
    attr8 TEXT,
    attr9 TEXT,
    attr10 TEXT,
    kyc_status VARCHAR(50),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_account_mobile_number
    ON $TenantSchema.account (mobile_number)
    WHERE mobile_number IS NOT NULL;

CREATE TABLE IF NOT EXISTS $TenantSchema.account_status_history (
    history_id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(100) NOT NULL,
    account_type VARCHAR(50),
    action_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    performed_by_type VARCHAR(50),
    reason VARCHAR(500),
    remarks VARCHAR(1000),
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_status_history_account
    ON $TenantSchema.account_status_history (account_id, performed_at DESC);

CREATE TABLE IF NOT EXISTS $TenantSchema.account_auth (
    auth_id BIGINT PRIMARY KEY,
    auth_hash VARCHAR(255),
    auth_value VARCHAR(255),
    auth_type VARCHAR(20) NOT NULL DEFAULT 'PIN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_attempts INTEGER DEFAULT 0,
    is_first_time_login BOOLEAN DEFAULT FALSE,
    last_failed_at TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    password_changed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.account_identifiers (
    id BIGSERIAL PRIMARY KEY,
    account_id TEXT NOT NULL,
    auth_id BIGINT NOT NULL,
    identifier_type VARCHAR(50) NOT NULL,
    identifier_value VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_account_identifiers_account FOREIGN KEY (account_id) REFERENCES $TenantSchema.account(account_id),
    CONSTRAINT fk_account_identifiers_auth FOREIGN KEY (auth_id) REFERENCES $TenantSchema.account_auth(auth_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_account_identifiers_active
    ON $TenantSchema.account_identifiers (identifier_type, identifier_value, status);

CREATE TABLE IF NOT EXISTS $TenantSchema.account_notification_endpoint (
    account_endpoint_id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(100) NOT NULL,
    endpoint_type VARCHAR(50) NOT NULL,
    endpoint_value VARCHAR(2000) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_on TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    field1 VARCHAR(250) NULL,
    field2 VARCHAR(250) NULL,
    field3 VARCHAR(250) NULL,
    field4 VARCHAR(250) NULL,
    field5 VARCHAR(250) NULL
);

CREATE INDEX IF NOT EXISTS idx_account_notification_endpoint_account_type
    ON $TenantSchema.account_notification_endpoint (account_id, endpoint_type);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_notification_endpoint_primary
    ON $TenantSchema.account_notification_endpoint (account_id, endpoint_type)
    WHERE is_primary = TRUE;

CREATE TABLE IF NOT EXISTS $TenantSchema.notification_template (
    template_id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(200) NOT NULL,
    template_definition JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    description VARCHAR(500),
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_template_code_status
    ON $TenantSchema.notification_template (template_code, status);

 INSERT INTO $TenantSchema.notification_template (template_code, template_definition, status, description, created_by) VALUES
('U2U.TRANSFER_SUCCESS.SENDER', '{"defaultLanguage":"en","defaultChannel":"SMS","channels":{"EMAIL":{"fromAddress":"noreply@bank.com","replyTo":"support@bank.com","priority":"HIGH","languages":{"en":{"subject":"Transfer Successful","body":"<html><body>Hello {{subscriberName}}, your transfer of {{amount}} {{currency}} was successful.</body></html>"}}},"SMS":{"senderId":"PAYBANK","languages":{"en":{"body":"Your transfer of {{amount}} {{currency}} was successful."}}},"PUSH":{"ttlSeconds":3600,"sound":"default","languages":{"en":{"title":"Transfer Successful","body":"{{amount}} {{currency}} transferred successfully."}}}}}'::jsonb, 'ACTIVE', 'P2P transfer success notification for sender', 'SYSTEM'),
('U2U.TRANSFER_SUCCESS.RECEIVER', '{"defaultLanguage":"en","defaultChannel":"PUSH","channels":{"SMS":{"senderId":"PAYBANK","languages":{"en":{"body":"You received {{amount}} {{currency}} from {{senderName}}."}}},"PUSH":{"ttlSeconds":3600,"sound":"default","languages":{"en":{"title":"Money Received","body":"{{senderName}} sent you {{amount}} {{currency}}."}}},"IN_APP":{"category":"PAYMENT","languages":{"en":{"title":"Money Received","body":"Amount credited successfully."}}}}}'::jsonb, 'ACTIVE', 'P2P transfer success notification for receiver', 'SYSTEM'),
('ACCOUNT.LOGIN_ALERT.SUBSCRIBER', '{"defaultLanguage":"en","defaultChannel":"EMAIL","channels":{"EMAIL":{"fromAddress":"security@bank.com","replyTo":"support@bank.com","priority":"HIGH","languages":{"en":{"subject":"New Login Detected","body":"<html><body>Hello {{subscriberName}}, a new login was detected on your account from {{location}} at {{loginTime}}.</body></html>"}}},"PUSH":{"ttlSeconds":1800,"sound":"alert","languages":{"en":{"title":"Login Alert","body":"New login detected from {{location}}."}}}}}'::jsonb, 'ACTIVE', 'Subscriber login alert notification', 'SECURITY_ADMIN'),
('BILLPAY.PAYMENT_FAILED.SUBSCRIBER', '{"defaultLanguage":"en","defaultChannel":"SMS","channels":{"SMS":{"senderId":"PAYBANK","languages":{"en":{"body":"Your bill payment of {{amount}} {{currency}} failed."}}},"EMAIL":{"fromAddress":"alerts@bank.com","replyTo":"support@bank.com","priority":"HIGH","languages":{"en":{"subject":"Bill Payment Failed","body":"<html><body>Your payment of {{amount}} {{currency}} could not be processed.</body></html>"}}}}}'::jsonb, 'ACTIVE', 'Bill payment failed notification', 'SYSTEM');

CREATE TABLE IF NOT EXISTS $TenantSchema.otp (
    otp_id BIGSERIAL PRIMARY KEY,
    reference_type VARCHAR(30) NOT NULL,
    reference_id VARCHAR(100),
    mobile_number VARCHAR(20),
    otp_value INTEGER,
    status VARCHAR(20),
    attempt_count INTEGER DEFAULT 0,
    max_attempt INTEGER DEFAULT 3,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE SEQUENCE IF NOT EXISTS $TenantSchema.wallet_wallet_id_seq START WITH 100000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS $TenantSchema.wallet (
    wallet_id BIGINT PRIMARY KEY DEFAULT nextval('$TenantSchema.wallet_wallet_id_seq'),
    account_id TEXT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    wallet_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    is_default BOOLEAN DEFAULT FALSE,
    is_locked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    remarks TEXT,
    CONSTRAINT fk_wallet_account FOREIGN KEY (account_id) REFERENCES $TenantSchema.account(account_id)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.wallet_balance (
    wallet_id BIGINT PRIMARY KEY,
    available_balance NUMERIC(19, 0) NOT NULL DEFAULT 0,
    frozen_balance NUMERIC(19, 0) NOT NULL DEFAULT 0,
    fic_balance NUMERIC(19, 0) NOT NULL DEFAULT 0,
    version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_balance_wallet FOREIGN KEY (wallet_id) REFERENCES $TenantSchema.wallet(wallet_id)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.wallet_restriction (
    wallet_id BIGINT PRIMARY KEY,
    restrictions JSONB NOT NULL,
    version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.wallet_restriction_history (
    history_id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    restrictions JSONB NOT NULL,
    action_type VARCHAR(50),
    changed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(wallet_id, version)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.roles (
    role_id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.permissions (
    permission_id BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    module VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES $TenantSchema.roles(role_id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES $TenantSchema.permissions(permission_id)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_by VARCHAR(50),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_account FOREIGN KEY (user_id) REFERENCES $TenantSchema.account(account_id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES $TenantSchema.roles(role_id)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.kyc_document (
    document_id BIGSERIAL PRIMARY KEY,
    account_id TEXT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    issue_date DATE,
    expiry_date DATE,
    document_url TEXT NOT NULL,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    verified_by VARCHAR(255),
    verified_at TIMESTAMP,
    rejection_reason TEXT,
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_document_account FOREIGN KEY (account_id) REFERENCES $TenantSchema.account(account_id)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.auth_challenge (
    challenge_id UUID PRIMARY KEY,
    account_id TEXT,
    challenge_value TEXT NOT NULL,
    challenge_type VARCHAR(30) NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    ip_address VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    CONSTRAINT fk_auth_challenge_account FOREIGN KEY (account_id) REFERENCES $TenantSchema.account(account_id)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.error_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    error_code VARCHAR(100) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    message_template TEXT NOT NULL,
    http_status INT NOT NULL DEFAULT 400 CHECK (http_status BETWEEN 100 AND 599),
    category VARCHAR(30),
    module VARCHAR(30),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by TEXT,
    CONSTRAINT uq_error_catalog_code_language UNIQUE (error_code, language_code)
);

ALTER TABLE $TenantSchema.error_catalog
    ADD COLUMN IF NOT EXISTS language_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS message_template TEXT,
    ADD COLUMN IF NOT EXISTS http_status INT DEFAULT 400,
    ADD COLUMN IF NOT EXISTS category VARCHAR(30),
    ADD COLUMN IF NOT EXISTS module VARCHAR(30),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS updated_by TEXT;

DO `$`$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = '$TenantSchema'
          AND table_name = 'error_catalog'
          AND column_name = 'language'
    ) THEN
        EXECUTE 'UPDATE $TenantSchema.error_catalog SET language_code = COALESCE(language_code, language)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = '$TenantSchema'
          AND table_name = 'error_catalog'
          AND column_name = 'message'
    ) THEN
        EXECUTE 'UPDATE $TenantSchema.error_catalog SET message_template = COALESCE(message_template, message)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = '$TenantSchema'
          AND table_name = 'error_catalog'
          AND column_name = 'status'
    ) THEN
        EXECUTE 'UPDATE $TenantSchema.error_catalog SET is_active = COALESCE(is_active, status = ''ACTIVE'')';
    END IF;
END
`$`$;

UPDATE $TenantSchema.error_catalog
SET http_status = COALESCE(http_status, 400),
    is_active = COALESCE(is_active, TRUE),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP);

CREATE UNIQUE INDEX IF NOT EXISTS uq_error_catalog_code_language
    ON $TenantSchema.error_catalog (error_code, language_code);

CREATE INDEX IF NOT EXISTS idx_error_catalog_lookup
    ON $TenantSchema.error_catalog (error_code, language_code);

CREATE TABLE IF NOT EXISTS $TenantSchema.categories (
    category_id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    description TEXT,
    status TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.tag_types (
    tag_type_id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(50) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    description TEXT,
    status TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.tags (
    tag_id BIGSERIAL PRIMARY KEY,
    tag_code VARCHAR(50) UNIQUE NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    is_default BOOLEAN DEFAULT FALSE NOT NULL,
    tag_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.account_tags (
    id BIGSERIAL PRIMARY KEY,
    account_id TEXT NOT NULL,
    tag_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    CONSTRAINT fk_account_tags_account FOREIGN KEY (account_id) REFERENCES $TenantSchema.account(account_id),
    CONSTRAINT fk_account_tags_tag FOREIGN KEY (tag_id) REFERENCES $TenantSchema.tags(tag_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_account_tags_account_tag
    ON $TenantSchema.account_tags (account_id, tag_id);

CREATE TABLE IF NOT EXISTS $TenantSchema.pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    pricing_name VARCHAR(100) NOT NULL,
    service_code VARCHAR(50) NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    pricing_type VARCHAR(20),
    payer VARCHAR(20) NOT NULL,
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

CREATE TABLE IF NOT EXISTS $TenantSchema.service_catalog (
    service_code VARCHAR(50) PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    service_category VARCHAR(50),
    transaction_type VARCHAR(50),
    is_financial BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.transactions (
    transaction_id VARCHAR(30) PRIMARY KEY,
    transfer_on TIMESTAMP,
    transaction_value NUMERIC(19, 0),
    error_code VARCHAR(200),
    transfer_status VARCHAR(3),
    request_gateway VARCHAR(10),
    service_code VARCHAR(15),
    trace_id VARCHAR(50),
    payment_reference VARCHAR(100),
    reconciliation_done VARCHAR(3),
    reconciliation_date TIMESTAMP,
    reconciliation_by VARCHAR(30),
    language VARCHAR(10),
    country VARCHAR(20),
    created_by VARCHAR(30) NOT NULL,
    created_on TIMESTAMP NOT NULL,
    modified_by VARCHAR(30) NOT NULL,
    modified_on TIMESTAMP NOT NULL,
    comments VARCHAR(300),
    debitor_account_id VARCHAR(30),
    creditor_account_id VARCHAR(30),
    debitor_wallet_type VARCHAR(50),
    debitor_currency VARCHAR(10),
    creditor_wallet_type VARCHAR(50),
    creditor_currency VARCHAR(10),
    fees_details VARCHAR(4000),
    additional_info VARCHAR(4000),
    metadata VARCHAR(4000),
    attr_1_name VARCHAR(255),
    attr_1_value VARCHAR(255),
    attr_2_name VARCHAR(255),
    attr_2_value VARCHAR(255),
    attr_3_name VARCHAR(255),
    attr_3_value VARCHAR(255),
    attr_4_name VARCHAR(255),
    attr_4_value VARCHAR(255),
    attr_5_name VARCHAR(255),
    attr_5_value VARCHAR(255),
    attr_6_name VARCHAR(255),
    attr_6_value VARCHAR(255),
    field1 VARCHAR(100),
    field2 VARCHAR(100),
    field3 VARCHAR(100),
    field4 VARCHAR(100),
    field5 VARCHAR(100),
    field6 VARCHAR(100),
    field7 VARCHAR(100),
    field8 VARCHAR(100),
    field9 VARCHAR(100),
    field10 VARCHAR(100),
    core_service_code VARCHAR(100),
    debitor_identifier_type VARCHAR(30),
    debitor_identifier_value VARCHAR(30),
    creditor_identifier_type VARCHAR(30),
    creditor_identifier_value VARCHAR(30),
    previous_status VARCHAR(5)
);

CREATE TABLE IF NOT EXISTS $TenantSchema.transaction_details (
    transaction_id VARCHAR(30) NOT NULL,
    txn_sequence_number BIGINT NOT NULL,
    account_id VARCHAR(30) NOT NULL,
    user_type VARCHAR(10) NOT NULL,
    entry_type VARCHAR(5) NOT NULL,
    identifier_id VARCHAR(80) NOT NULL,
    second_identifier_id VARCHAR(80) NOT NULL,
    transaction_value NUMERIC(19, 0),
    approved_value NUMERIC(19, 0),
    previous_balance NUMERIC(19, 0),
    post_balance NUMERIC(19, 0),
    transfer_on TIMESTAMP,
    service_code VARCHAR(15) NOT NULL,
    transfer_status VARCHAR(3),
    wallet_number VARCHAR(25),
    wallet_type VARCHAR(50),
    currency VARCHAR(10),
    transaction_type VARCHAR(50),
    previous_fic_balance NUMERIC(19, 0),
    post_fic_balance NUMERIC(19, 0),
    previous_frozen_balance NUMERIC(19, 0),
    post_frozen_balance NUMERIC(19, 0),
    attr_1_name VARCHAR(255),
    attr_1_value VARCHAR(255),
    attr_2_name VARCHAR(255),
    attr_2_value VARCHAR(255),
    attr_3_name VARCHAR(255),
    attr_3_value VARCHAR(255),
    attr_4_name VARCHAR(255),
    attr_4_value VARCHAR(255),
    attr_5_name VARCHAR(255),
    attr_5_value VARCHAR(255),
    attr_6_name VARCHAR(255),
    attr_6_value VARCHAR(255),
    PRIMARY KEY (transaction_id, txn_sequence_number)
);

ALTER TABLE $TenantSchema.transactions
    ADD COLUMN IF NOT EXISTS debitor_wallet_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS debitor_currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS creditor_wallet_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS creditor_currency VARCHAR(10);

ALTER TABLE $TenantSchema.transaction_details
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
    ADD COLUMN IF NOT EXISTS attr_6_value VARCHAR(255);

CREATE TABLE IF NOT EXISTS $TenantSchema.wallet_ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    txn_id VARCHAR(255) NOT NULL,
    wallet_id BIGINT NOT NULL,
    account_id TEXT NOT NULL,
    entry_type VARCHAR(2) NOT NULL,
    amount NUMERIC(19, 0) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    balance_before NUMERIC(19, 0),
    balance_after NUMERIC(19, 0),
    txn_type VARCHAR(255),
    reference_type VARCHAR(255),
    reference_id VARCHAR(255),
    description TEXT,
    attr1 TEXT,
    attr2 TEXT,
    attr3 TEXT,
    attr4 TEXT,
    attr5 TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS $TenantSchema.bill_payment_status (
    transaction_id VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    subscriber_account_id VARCHAR(255) NOT NULL,
    biller_account_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(255) NOT NULL,
    comments TEXT,
    additional_info TEXT,
    rollback_transaction_id VARCHAR(255),
    settled_by VARCHAR(255),
    settled_on TIMESTAMP,
    created_on TIMESTAMP NOT NULL,
    modified_on TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS $TenantSchema.cashback_payout (
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
    ON $TenantSchema.cashback_payout(status, pay_at);

CREATE TABLE IF NOT EXISTS $TenantSchema.audit_api_log (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(255),
    trace_id VARCHAR(255),
    tenant_id VARCHAR(255),
    http_method VARCHAR(20),
    endpoint TEXT,
    status_code INTEGER,
    request_payload TEXT,
    response_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO `$`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$DbUser') THEN
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', '$DbUser', '$DbPassword');
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', '$DbUser', '$DbPassword');
    END IF;
END
`$`$;

INSERT INTO $TenantSchema.enumerations (enum_type, enum_code, enum_value, description, display_order, is_active, is_system)
VALUES
    ('SYSTEM_CONFIG', 'TESTING_MODE', 'true', 'Set to true to use fixed OTP, PIN, and password values for testing', 0, TRUE, TRUE),
    ('SYSTEM_CONFIG', 'INTRAWALLET_BONUS_TO_MAIN_PERCENTAGE', '25', 'Percentage of bonus wallet value credited to main wallet during intra-wallet transfers', 1, TRUE, TRUE),
( 'ACCOUNT_STATUS', 'ACTIVE', 'Active', 'Account is active', 1, TRUE, TRUE),
( 'ACCOUNT_STATUS', 'LOCKED', 'Locked', 'Account locked due to failed login', 2, TRUE, TRUE),
( 'ACCOUNT_STATUS', 'DISABLED', 'Disabled', 'Account disabled by admin', 3, TRUE, TRUE),
( 'ACCOUNT_STATUS', 'SUSPENDED', 'Suspended', 'Temporarily suspended', 4, TRUE, TRUE),
('AUTH_TYPE', 'PASSWORD', 'Password', 'Password authentication', 1, TRUE, TRUE),
('AUTH_TYPE', 'OTP', 'One Time Password', 'OTP based login', 2, TRUE, TRUE),
('AUTH_TYPE', 'API_KEY', 'API Key', 'API authentication', 3, TRUE, TRUE),
('AUTH_TYPE', 'PIN', 'Pin', 'Pin Authentication', 4, TRUE, TRUE),
( 'WALLET_TYPE', 'MAIN', 'Main Wallet', 'Primary wallet', 1, TRUE, TRUE),
( 'WALLET_TYPE', 'BONUS', 'Bonus Wallet', 'Promotional wallet', 2, TRUE, TRUE),
('WALLET_TYPE', 'COMMISSION', 'Commission Wallet', 'Commission wallet', 3, FALSE, TRUE),
('WALLET_TYPE', 'SALARY', 'Salary Wallet', 'Salary wallet', 4, FALSE, TRUE),
( 'TXN_STATUS', 'PENDING', 'Pending', 'Transaction initiated', 1, TRUE, TRUE),
( 'TXN_STATUS', 'SUCCESS', 'Success', 'Transaction completed', 2, TRUE, TRUE),
( 'TXN_STATUS', 'FAILED', 'Failed', 'Transaction failed', 3, TRUE, TRUE),
( 'TXN_STATUS', 'REVERSED', 'Reversed', 'Transaction reversed', 4, TRUE, TRUE),
( 'CURRENCY', 'USD', 'US Dollar', 'US Dollar', 1, TRUE, TRUE),
( 'CURRENCY', 'EUR', 'Euro', 'Euro', 2, TRUE, TRUE),
( 'CURRENCY', 'INR', 'Indian Rupee', 'Indian Rupee', 3, TRUE, TRUE),
( 'CURRENCY', 'COP', 'Colombian Peso', 'Colombian Peso', 4, FALSE, TRUE)
ON CONFLICT (enum_type, enum_code) DO UPDATE
SET enum_value = EXCLUDED.enum_value,
    description = EXCLUDED.description,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.supported_languages (
    language_code,
    language_name,
    is_default,
    is_active,
    display_order,
    created_at,
    updated_at
)
VALUES (
    'en',
    'English',
    TRUE,
    TRUE,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (language_code) DO UPDATE
SET language_name = EXCLUDED.language_name,
    is_default = TRUE,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.error_catalog
    (error_code, language_code, message_template, http_status, category, module, is_active)
VALUES
    ('INTERNAL_ERROR', 'en', 'Something went wrong', 500, 'SYSTEM', 'COMMON', TRUE),
    ('INVALID_REQUEST_BODY', 'en', 'Malformed or unreadable request body', 400, 'VALIDATION', 'COMMON', TRUE),
    ('INVALID_ENUM_VALUE', 'en', 'Invalid value ''{value}'' for field ''{field}''. Allowed values: {allowedValues}', 400, 'VALIDATION', 'COMMON', TRUE),
    ('VALIDATION_ERROR', 'en', '{detail}', 400, 'VALIDATION', 'COMMON', TRUE),
    ('DEFAULT_LANGUAGE_NOT_CONFIGURED', 'en', 'No active default language configured', 500, 'SYSTEM', 'COMMON', TRUE),
    ('AUTHENTICATION_REQUIRED', 'en', 'Authentication required', 401, 'AUTH', 'COMMON', TRUE),
    ('ACCESS_DENIED', 'en', 'Access denied', 403, 'AUTH', 'COMMON', TRUE),
    ('INVALID_TOKEN', 'en', 'Bearer token is invalid or expired', 401, 'AUTH', 'COMMON', TRUE),
    ('TENANT_HEADER_MISSING', 'en', 'X-Tenant-Id header is required', 400, 'VALIDATION', 'COMMON', TRUE),
    ('UNKNOWN_TENANT', 'en', 'Unknown tenant', 404, 'VALIDATION', 'COMMON', TRUE),
    ('SERVICE_UNAVAILABLE', 'en', 'Service is temporarily unavailable', 503, 'SYSTEM', 'COMMON', TRUE),
    ('REQUEST_TIMEOUT', 'en', 'Request timed out', 408, 'SYSTEM', 'COMMON', TRUE),
    ('UNSUPPORTED_MEDIA_TYPE', 'en', 'Unsupported content type', 415, 'VALIDATION', 'COMMON', TRUE),
    ('TOO_MANY_REQUESTS', 'en', 'Too many requests, please try later', 429, 'SYSTEM', 'COMMON', TRUE),
    ('SUSPICIOUS_ACTIVITY', 'en', 'Suspicious activity detected', 403, 'SECURITY', 'COMMON', TRUE),
    ('TOKEN_EXPIRED', 'en', 'Token has expired', 401, 'AUTH', 'COMMON', TRUE),
    ('INVALID_SIGNATURE', 'en', 'Token signature is invalid', 401, 'AUTH', 'COMMON', TRUE),
    ('OPERATION_TYPE_MISSING', 'en', 'Operation type is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('OPERATION_NOT_ALLOWED', 'en', '{operationType} operation is not allowed', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('REQUEST_GATEWAY_MISSING', 'en', 'requestGateway is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('INITIATED_BY_MISSING', 'en', 'initiatedBy is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('DEBTOR_MISSING', 'en', 'Debtor details are required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('DEBITOR_MISSING', 'en', 'Debitor details are required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('CREDITOR_MISSING', 'en', 'Creditor details are required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('WALLET_TYPE_MISSING', 'en', 'walletType is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('IDENTIFIER_MISSING', 'en', 'Identifier is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('IDENTIFIER_TYPE_MISSING', 'en', 'Identifier type is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('IDENTIFIER_VALUE_MISSING', 'en', 'Identifier value is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('AUTH_TYPE_MISSING', 'en', 'Authentication type is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('AUTH_VALUE_MISSING', 'en', 'Authentication value is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('TRANSACTION_MISSING', 'en', 'Transaction details required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('INVALID_AMOUNT', 'en', 'Transaction amount must be greater than zero', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('INVALID_AMOUNT_SCALE', 'en', 'Transaction amount can have at most 2 decimal places', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('PAYMENT_REFERENCE_TOO_LONG', 'en', 'paymentReference can be at most 100 characters', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('COMMENTS_TOO_LONG', 'en', 'comments can be at most 300 characters', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('CURRENCY_MISSING', 'en', 'Currency is required', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('INVALID_CURRENCY', 'en', 'Currency {currency} is not supported', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('AMOUNT_LIMIT_EXCEEDED', 'en', 'Transaction amount exceeds allowed limit', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('MIN_AMOUNT_NOT_MET', 'en', 'Minimum transaction amount not met', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('SELF_TRANSFER_NOT_ALLOWED', 'en', 'Debitor and creditor cannot be the same account', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_DEBITOR_USER_TYPE', 'en', 'DEBITOR user type {accountType} not allowed for {operationType}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_CREDITOR_USER_TYPE', 'en', 'CREDITOR user type {accountType} not allowed for {operationType}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_DEBITOR_ACCOUNT_TYPE', 'en', 'DEBITOR account type mismatch', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_CREDITOR_ACCOUNT_TYPE', 'en', 'CREDITOR account type mismatch', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_INITIATOR', 'en', 'Initiator {initiatedBy} not allowed', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('CROSS_WALLET_TRANSFER_NOT_ALLOWED', 'en', 'Debitor and creditor walletType must be the same for {operationType}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('ACCOUNT_IDENTIFIER_NOT_FOUND', 'en', 'Identifier not found for value: {identifierValue}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('ACCOUNT_NOT_FOUND', 'en', 'Account not found for identifier: {identifierValue}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_NOT_FOUND', 'en', '{role} wallet not found for currency {currency} and walletType {walletType}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_WALLET', 'en', '{role} wallet is not active', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_LOCKED', 'en', '{role} wallet is locked', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_SEND_BLOCKED', 'en', 'Wallet {walletId} is blocked from sending for service {serviceCode}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_RECEIVE_BLOCKED', 'en', 'Wallet {walletId} is blocked from receiving for service {serviceCode}', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INSUFFICIENT_BALANCE', 'en', 'Insufficient balance', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_BALANCE_NOT_FOUND', 'en', 'Wallet balance not found', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_INSUFFICIENT_FUNDS', 'en', 'Wallet has insufficient funds', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('WALLET_CURRENCY_MISMATCH', 'en', 'Wallet currency does not match transaction currency', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('DUPLICATE_TRANSACTION', 'en', 'Duplicate transaction detected', 409, 'BUSINESS', 'PAYMENT', TRUE),
    ('TRANSACTION_NOT_FOUND', 'en', 'Transaction not found', 404, 'BUSINESS', 'PAYMENT', TRUE),
    ('TXN_NOT_FOUND', 'en', 'Transaction not found', 404, 'BUSINESS', 'PAYMENT', TRUE),
    ('TRANSACTION_ALREADY_PROCESSED', 'en', 'Transaction already processed', 409, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_TRANSACTION_STATUS', 'en', 'Invalid transaction status', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_TRANSACTION_TYPE', 'en', 'Invalid transaction type', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('INVALID_TRANSACTION_DETAILS', 'en', 'Invalid transaction details', 400, 'BUSINESS', 'PAYMENT', TRUE),
    ('TRANSACTION_DETAIL_NOT_FOUND', 'en', 'Transaction detail not found', 404, 'BUSINESS', 'PAYMENT', TRUE),
    ('UNAUTHORIZED', 'en', 'Valid JWT token is required', 401, 'AUTH', 'PAYMENT', TRUE),
    ('INVALID_PRIVILEGES', 'en', 'Token does not have necessary access', 403, 'AUTH', 'PAYMENT', TRUE),
    ('INVALID_AUTH_TYPE', 'en', 'Authentication type does not match account setup', 400, 'AUTH', 'PAYMENT', TRUE),
    ('ACCOUNT_AUTH_NOT_FOUND', 'en', 'Authorization record not found', 400, 'AUTH', 'PAYMENT', TRUE),
    ('ACCOUNT_AUTH_INACTIVE', 'en', 'Authentication record is not active', 400, 'AUTH', 'PAYMENT', TRUE),
    ('ACCOUNT_LOCKED', 'en', 'Account is locked', 400, 'AUTH', 'PAYMENT', TRUE),
    ('INVALID_PIN', 'en', 'Invalid transaction PIN.', 400, 'AUTH', 'PAYMENT', TRUE),
    ('INVALID_PASSWORD', 'en', 'Invalid password', 400, 'AUTH', 'PAYMENT', TRUE),
    ('INVALID_CREDITOR_IDENTIFIER_TYPE', 'en', 'Invalid creditor identifier type for {operationType}. Allowed values for {accountType}: {allowedTypes}', 400, 'VALIDATION', 'PAYMENT', TRUE),
    ('TRANSACTION_FAILED', 'en', 'Transaction failed due to internal error', 500, 'SYSTEM', 'PAYMENT', TRUE)
ON CONFLICT (error_code, language_code) DO UPDATE
SET message_template = EXCLUDED.message_template,
    http_status = EXCLUDED.http_status,
    category = EXCLUDED.category,
    module = EXCLUDED.module,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.roles (role_code, role_name, role_type, description, status)
VALUES
    ('SUPERADMIN', 'Super Admin', 'ADMIN', 'Default super administrator role', 'ACTIVE'),
    ('NETWORKADMIN', 'Network Admin', 'ADMIN', 'Network administrator role', 'ACTIVE'),
    ('SUBSCRIBER', 'Subscriber', 'SUBSCRIBER', 'Default subscriber role', 'ACTIVE'),
    ('ADMIN', 'Admin', 'ADMIN', 'Administrator role', 'ACTIVE'),
    ('AGENT', 'Agent', 'AGENT', 'Agent role', 'ACTIVE'),
    ('MERCHANT', 'Merchant', 'MERCHANT', 'Merchant role', 'ACTIVE'),
    ('BILLER', 'Biller', 'BILLER', 'Biller role', 'ACTIVE')
ON CONFLICT (role_code) DO UPDATE
SET role_name = EXCLUDED.role_name,
    role_type = EXCLUDED.role_type,
    description = EXCLUDED.description,
    status = 'ACTIVE';

INSERT INTO $TenantSchema.account (
    account_id,
    account_type,
    first_name,
    last_name,
    mobile_number,
    email,
    preferred_lang,
    kyc_status,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by
)
VALUES (
    '$DefaultAdminAccountId',
    'ADMIN',
    'Default',
    'Admin',
    '$DefaultAdminMobile',
    '$DefaultAdminEmail',
    'en',
    'VERIFIED',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'bootstrap',
    'bootstrap'
)
ON CONFLICT (account_id) DO UPDATE
SET account_type = EXCLUDED.account_type,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    mobile_number = EXCLUDED.mobile_number,
    email = EXCLUDED.email,
    preferred_lang = EXCLUDED.preferred_lang,
    kyc_status = EXCLUDED.kyc_status,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'bootstrap';

INSERT INTO $TenantSchema.account_auth (
    auth_id,
    auth_hash,
    auth_value,
    auth_type,
    status,
    failed_attempts,
    is_first_time_login,
    password_changed_at,
    created_at,
    updated_at
)
VALUES (
    900000000000001,
    '$DefaultAdminAuthSalt',
    '$DefaultAdminPasswordHash',
    'PASSWORD',
    'ACTIVE',
    0,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (auth_id) DO UPDATE
SET auth_hash = EXCLUDED.auth_hash,
    auth_value = EXCLUDED.auth_value,
    auth_type = EXCLUDED.auth_type,
    status = 'ACTIVE',
    failed_attempts = 0,
    is_first_time_login = FALSE,
    password_changed_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.account_identifiers (
    account_id,
    auth_id,
    identifier_type,
    identifier_value,
    status,
    created_at,
    updated_at
)
VALUES (
    '$DefaultAdminAccountId',
    900000000000001,
    'LOGINID',
    '$DefaultAdminLoginId',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (identifier_type, identifier_value, status) DO UPDATE
SET account_id = EXCLUDED.account_id,
    auth_id = EXCLUDED.auth_id,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.account_notification_endpoint (
    account_id,
    endpoint_type,
    endpoint_value,
    is_primary,
    status,
    created_on,
    updated_at
)
VALUES
    ('$DefaultAdminAccountId', 'MOBILE', '$DefaultAdminMobile', TRUE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('$DefaultAdminAccountId', 'EMAIL', '$DefaultAdminEmail', TRUE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (account_id, endpoint_type) WHERE is_primary = TRUE DO UPDATE
SET endpoint_value = EXCLUDED.endpoint_value,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.user_roles (user_id, role_id, assigned_by, assigned_at)
SELECT '$DefaultAdminAccountId', role_id, 'bootstrap', CURRENT_TIMESTAMP
FROM $TenantSchema.roles
WHERE role_code = 'SUPERADMIN'
ON CONFLICT (user_id, role_id) DO UPDATE
SET assigned_by = EXCLUDED.assigned_by,
    assigned_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.account (
    account_id,
    account_type,
    first_name,
    last_name,
    preferred_lang,
    kyc_status,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by
)
VALUES (
    'SYS0001',
    'ADMIN',
    'System',
    'Operator',
    'en',
    'VERIFIED',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'bootstrap',
    'bootstrap'
)
ON CONFLICT (account_id) DO UPDATE
SET account_type = EXCLUDED.account_type,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    preferred_lang = EXCLUDED.preferred_lang,
    kyc_status = EXCLUDED.kyc_status,
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'bootstrap';

INSERT INTO $TenantSchema.wallet (
    wallet_id,
    account_id,
    currency,
    wallet_type,
    status,
    is_default,
    is_locked,
    created_at,
    updated_at,
    remarks
)
VALUES
    (90000000000, 'SYS0001', 'USD', 'MAIN', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Treasury account for USD Wallets'),
    (90000000001, 'SYS0001', 'INR', 'MAIN', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Treasury account for INR Wallets'),
    (90000000002, 'SYS0001', 'EUR', 'MAIN', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Treasury account for EURO Wallets'),
    (90000000010, 'SYS0001', 'USD', 'BANK', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Bank account for USD Wallets'),
    (90000000011, 'SYS0001', 'INR', 'BANK', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Bank account for INR Wallets'),
    (90000000012, 'SYS0001', 'EUR', 'BANK', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Bank account for EUR Wallets'),
    (90000000020, 'SYS0001', 'USD', 'SC', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Service Charge account for USD Wallets'),
    (90000000021, 'SYS0001', 'INR', 'SC', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Service Charge account for INR Wallets'),
    (90000000022, 'SYS0001', 'EUR', 'SC', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Service Charge account for EUR Wallets'),
    (90000000030, 'SYS0001', 'USD', 'COMMDIS', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Commission disbursement account for USD Wallets'),
    (90000000031, 'SYS0001', 'INR', 'COMMDIS', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Commission disbursement account for INR Wallets'),
    (90000000032, 'SYS0001', 'EUR', 'COMMDIS', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'Commission disbursement account for EUR Wallets'),
    (90000000040, 'SYS0001', 'USD', 'TAX', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'TAX account for USD Wallets'),
    (90000000041, 'SYS0001', 'INR', 'TAX', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'TAX account for INR Wallets'),
    (90000000042, 'SYS0001', 'EUR', 'TAX', 'ACTIVE', TRUE, FALSE, CURRENT_TIMESTAMP, NULL, 'TAX account for EUR Wallets')
ON CONFLICT (wallet_id) DO UPDATE
SET account_id = EXCLUDED.account_id,
    currency = EXCLUDED.currency,
    wallet_type = EXCLUDED.wallet_type,
    status = 'ACTIVE',
    is_default = EXCLUDED.is_default,
    is_locked = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    remarks = EXCLUDED.remarks;

INSERT INTO $TenantSchema.wallet_balance (
    wallet_id,
    available_balance,
    frozen_balance,
    fic_balance,
    version,
    updated_at
)
VALUES
    (90000000000, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000001, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000002, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000010, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000011, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000012, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000020, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000021, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000022, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000030, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000031, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000032, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000040, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000041, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (90000000042, 0, 0, 0, 0, CURRENT_TIMESTAMP)
ON CONFLICT (wallet_id) DO UPDATE
SET updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.categories (category_code, category_name, description, status)
VALUES
    ('SUBSCRIBER', 'Subscriber', 'End user of the system', 'ACTIVE'),
    ('MERCHANT', 'Merchant', 'Business accepting payments', 'ACTIVE'),
    ('AGENT', 'Agent', 'Intermediary or distributor', 'ACTIVE'),
    ('PARTNER', 'Partner', 'External partner integrations', 'ACTIVE'),
    ('ADMIN', 'Admin', 'Admin user for the system', 'ACTIVE')
ON CONFLICT (category_code) DO UPDATE
SET category_name = EXCLUDED.category_name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.tag_types (
    tag_type_id,
    type_code,
    type_name,
    description,
    status,
    created_at,
    updated_at
)
VALUES
    (1, 'BASE', 'Base Category Tag', 'Default tag assigned based on user category', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'UPGRADE', 'Upgrade Tag', 'Premium or paid upgrade tags', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'BEHAVIOR', 'Behavior Tag', 'Derived from user activity like high volume', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'RISK', 'Risk Tag', 'Risk classification tags like high/low risk', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (type_code) DO UPDATE
SET type_name = EXCLUDED.type_name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

SELECT setval(
    pg_get_serial_sequence('$TenantSchema.tag_types', 'tag_type_id'),
    GREATEST((SELECT COALESCE(MAX(tag_type_id), 1) FROM $TenantSchema.tag_types), 1)
);

INSERT INTO $TenantSchema.tags (
    tag_code,
    tag_name,
    category,
    is_default,
    tag_type,
    status,
    created_at,
    updated_at
)
VALUES
    ('SUBSCRIBER_BASE', 'Subscriber Base', 'SUBSCRIBER', TRUE, 'BASE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MERCHANT_BASE', 'Merchant Base', 'MERCHANT', TRUE, 'BASE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('AGENT_BASE', 'Agent Base', 'AGENT', TRUE, 'BASE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PARTNER_BASE', 'Partner Base', 'PARTNER', TRUE, 'BASE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tag_code) DO UPDATE
SET tag_name = EXCLUDED.tag_name,
    category = EXCLUDED.category,
    is_default = EXCLUDED.is_default,
    tag_type = EXCLUDED.tag_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO $TenantSchema.service_catalog (
    service_code,
    service_name,
    description,
    service_category,
    transaction_type,
    is_financial,
    is_active,
    display_order
)
VALUES
    ('U2U', 'User to User Transfer', 'User to user transfer', 'PAYMENT', 'TRANSFER', TRUE, TRUE, 1),
    ('MERCHANTPAY', 'Merchant Payment', 'Merchant payment', 'PAYMENT', 'PAYMENT', TRUE, TRUE, 2),
    ('CASHIN', 'Cash In', 'Cash in', 'PAYMENT', 'CREDIT', TRUE, TRUE, 3),
    ('CASHOUT', 'Cash Out', 'Cash out', 'PAYMENT', 'DEBIT', TRUE, TRUE, 4),
    ('BILLPAY', 'Bill Payment', 'Bill payment', 'PAYMENT', 'PAYMENT', TRUE, TRUE, 5),
    ('O2C', 'Operator to Channel Transfer', 'Operator to channel wallet transfer', 'PAYMENT', 'TRANSFER', TRUE, TRUE, 6),
    ('ACCOUNT_DELETION', 'Account Deletion', 'Subscriber account deletion balance transfer', 'SYSTEM', 'TRANSFER', TRUE, TRUE, 7),
    ('INTRAWALLET', 'Intra Wallet Transfer', 'Same account transfer between wallets of different currencies', 'PAYMENT', 'TRANSFER', TRUE, TRUE, 8)
ON CONFLICT (service_code) DO UPDATE
SET service_name = EXCLUDED.service_name,
    description = EXCLUDED.description,
    service_category = EXCLUDED.service_category,
    transaction_type = EXCLUDED.transaction_type,
    is_financial = EXCLUDED.is_financial,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    updated_at = CURRENT_TIMESTAMP;



CREATE TABLE IF NOT EXISTS $TenantSchema.fx_rates (
    rate_id BIGSERIAL PRIMARY KEY,
    target_currency CHAR(3) NOT NULL,
    usd_rate NUMERIC(20,10) NOT NULL,
    rate_type VARCHAR(20) NOT NULL DEFAULT 'MID',
    provider VARCHAR(50) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    version_no BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	created_by VARCHAR(50) NOT NULL,
	field1 VARCHAR(100),
	field2 VARCHAR(100),
	field3 VARCHAR(100),
	field4 VARCHAR(100),
	field5 VARCHAR(100),
    CONSTRAINT uq_fx_active
        UNIQUE(target_currency, version_no)
);

INSERT INTO $TenantSchema.fx_rates (
    target_currency,
    usd_rate,
    rate_type,
    provider,
    valid_from,
    version_no,
    is_active,
    created_by,
    field1,
    field2,
    field3,
    field4,
    field5
) VALUES
(
    'INR',
    83.1200000000,
    'MID',
    'INTERNAL',
    '2026-05-08 09:00:00',
    1,
    TRUE,
    'SYSTEM',
    'ASIA',
    NULL,
    NULL,
    NULL,
    NULL
),
(
    'EUR',
    0.9200000000,
    'MID',
    'INTERNAL',
    '2026-05-08 09:00:00',
    1,
    TRUE,
    'SYSTEM',
    'EUROPE',
    NULL,
    NULL,
    NULL,
    NULL
),
(
    'COP',
    4100.0000000000,
    'MID',
    'INTERNAL',
    '2026-05-08 09:00:00',
    1,
    TRUE,
    'SYSTEM',
    'LATAM',
    NULL,
    NULL,
    NULL,
    NULL
)
ON CONFLICT (target_currency, version_no) DO NOTHING;


GRANT USAGE, CREATE ON SCHEMA $TenantSchema TO $DbUser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA $TenantSchema TO $DbUser;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA $TenantSchema TO $DbUser;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenant_registry TO $DbUser;

COMMIT;
"@

try {

    # Write SQL file as UTF-8 WITHOUT BOM
    [System.IO.File]::WriteAllText(
        $sqlFile,
        $sql,
        [System.Text.UTF8Encoding]::new($false)
    )
    $sqlFileInfo = Get-Item -LiteralPath $sqlFile
    Write-DbBootstrapLog "Wrote bootstrap SQL file. sqlFile=$sqlFile lengthBytes=$($sqlFileInfo.Length)"

    Invoke-PsqlCommand `
        -StepName "execute-bootstrap-sql" `
        -Arguments @(
            "-h", $DbHost,
            "-p", "$DbPort",
            "-U", $DbLoginUser,
            "-d", $Database,
            "-v", "ON_ERROR_STOP=1",
            "-f", $sqlFile
        )

    $verificationSql = @"
SELECT 'tenant_registry' AS check_name,
       COUNT(*)::text AS value
FROM public.tenant_registry
WHERE tenant_id = '$TenantId'
  AND schema_name = '$TenantSchema'
  AND status = 'ACTIVE'
UNION ALL
SELECT 'schema_exists',
       COUNT(*)::text
FROM information_schema.schemata
WHERE schema_name = '$TenantSchema'
UNION ALL
SELECT 'tenant_tables',
       COUNT(*)::text
FROM information_schema.tables
WHERE table_schema = '$TenantSchema'
UNION ALL
SELECT 'admin_account',
       COUNT(*)::text
FROM $TenantSchema.account
WHERE account_id = '$DefaultAdminAccountId'
UNION ALL
SELECT 'admin_identifier',
       COUNT(*)::text
FROM $TenantSchema.account_identifiers
WHERE identifier_type = 'LOGINID'
  AND identifier_value = '$DefaultAdminLoginId'
UNION ALL
SELECT 'roles',
       COUNT(*)::text
FROM $TenantSchema.roles
UNION ALL
SELECT 'enumerations',
       COUNT(*)::text
FROM $TenantSchema.enumerations
UNION ALL
SELECT 'service_catalog',
       COUNT(*)::text
FROM $TenantSchema.service_catalog;
"@

    Write-DbBootstrapLog "Running post-bootstrap verification queries for tenantId=$TenantId tenantSchema=$TenantSchema"
    Invoke-PsqlCommand `
        -StepName "verify-bootstrap-state" `
        -Arguments @(
            "-h", $DbHost,
            "-p", "$DbPort",
            "-U", $DbLoginUser,
            "-d", $Database,
            "-v", "ON_ERROR_STOP=1",
            "-P", "pager=off",
            "-c", $verificationSql
        )

    Write-DbBootstrapLog "PayNest database bootstrap completed for tenant '$TenantId' and schema '$TenantSchema'."

} finally {

    Write-DbBootstrapLog "Cleaning bootstrap resources. sqlFile=$sqlFile"
    Remove-Item -LiteralPath $sqlFile -Force -ErrorAction SilentlyContinue
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Write-DbBootstrapLog "Bootstrap cleanup completed for tenant '$TenantId'."
}
