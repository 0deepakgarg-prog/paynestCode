param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$Database = "postgres",
    [string]$DbLoginUser = "postgres",
    [string]$DatabasePwd = "postgres",
    [string]$DbUser = "paynest_app",
    [string]$DbPassword = "paynest_app"
)

$ErrorActionPreference = "Stop"

function Write-MasterDbBootstrapLog {
    param(
        [string]$Message
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
    Write-Host "[$timestamp] [master-db-bootstrap] $Message"
}

function Resolve-PsqlCommand {
    $command = Get-Command psql -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidateRoots = @(
        $env:ProgramFiles,
        [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
    ) | Where-Object { $_ -and (Test-Path $_) }

    foreach ($root in $candidateRoots) {
        $postgresRoot = Join-Path $root "PostgreSQL"
        if (-not (Test-Path $postgresRoot)) {
            continue
        }

        $psqlCandidates = Get-ChildItem -Path $postgresRoot -Filter psql.exe -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\bin\\psql\.exe$' } |
            Sort-Object FullName -Descending

        if ($psqlCandidates) {
            return $psqlCandidates[0].FullName
        }
    }

    throw "psql was not found on PATH or under Program Files\\PostgreSQL. Install PostgreSQL client tools or add psql.exe to PATH."
}

function Invoke-PsqlCommand {
    param(
        [string[]]$Arguments,
        [string]$StepName
    )

    Write-MasterDbBootstrapLog "Starting psql step '$StepName'. arguments=$($Arguments -join ' ')"
    $startedAt = Get-Date
    & $script:PsqlCommand @Arguments
    $exitCode = $LASTEXITCODE
    $durationMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
    Write-MasterDbBootstrapLog "Finished psql step '$StepName'. exitCode=$exitCode durationMs=$durationMs"

    if ($exitCode -ne 0) {
        throw "psql step '$StepName' exited with code $exitCode"
    }
}

Write-MasterDbBootstrapLog "Bootstrap parameters: dbHost=$DbHost dbPort=$DbPort database=$Database dbLoginUser=$DbLoginUser dbUser=$DbUser"

$script:PsqlCommand = Resolve-PsqlCommand
Write-MasterDbBootstrapLog "Found psql. path=$script:PsqlCommand"

$env:PGPASSWORD = $DatabasePwd
$sqlFile = Join-Path ([System.IO.Path]::GetTempPath()) ("paynest-master-bootstrap-{0}.sql" -f ([guid]::NewGuid()))
Write-MasterDbBootstrapLog "Generated temporary SQL file path. sqlFile=$sqlFile"

$sql = @"
BEGIN;

CREATE TABLE IF NOT EXISTS public.tenant_registry (
    tenant_id VARCHAR(50) PRIMARY KEY,
    tenant_name VARCHAR(100),
    schema_name VARCHAR(100) NOT NULL,
    iana_time_zone VARCHAR(100),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.tenant_registry
    ADD COLUMN IF NOT EXISTS tenant_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS schema_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS iana_time_zone VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE public.tenant_registry
SET updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP);

CREATE TABLE IF NOT EXISTS public.system_config (
    config_id BIGSERIAL PRIMARY KEY,
    config_key TEXT UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    config_type TEXT NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by TEXT,
    CONSTRAINT chk_system_config_type
        CHECK (config_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON'))
);

ALTER TABLE public.system_config
    ADD COLUMN IF NOT EXISTS config_key TEXT,
    ADD COLUMN IF NOT EXISTS config_value TEXT,
    ADD COLUMN IF NOT EXISTS config_type TEXT,
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_by TEXT;

DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_system_config_type'
          AND conrelid = 'public.system_config'::regclass
    ) THEN
        ALTER TABLE public.system_config
            ADD CONSTRAINT chk_system_config_type
            CHECK (config_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON'));
    END IF;
END
`$`$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_config_key
    ON public.system_config (config_key);

UPDATE public.system_config
SET updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    is_active = COALESCE(is_active, TRUE);

CREATE TABLE IF NOT EXISTS public.audit_api_logs (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(100),
    tenant_id VARCHAR(50),
    http_method VARCHAR(10),
    api_path VARCHAR(255),
    account_id VARCHAR(50),
    service_code VARCHAR(50),
    reference_id VARCHAR(100),
    transaction_id VARCHAR(50),
    request_body JSONB,
    response_body JSONB,
    http_status INT,
    processing_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.audit_api_logs
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS http_method VARCHAR(10),
    ADD COLUMN IF NOT EXISTS api_path VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS service_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reference_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS request_body JSONB,
    ADD COLUMN IF NOT EXISTS response_body JSONB,
    ADD COLUMN IF NOT EXISTS http_status INT,
    ADD COLUMN IF NOT EXISTS processing_time_ms BIGINT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_audit_api_logs_trace_id
    ON public.audit_api_logs (trace_id);

CREATE INDEX IF NOT EXISTS idx_audit_api_logs_service_code
    ON public.audit_api_logs (service_code);

CREATE INDEX IF NOT EXISTS idx_audit_api_logs_reference_id
    ON public.audit_api_logs (reference_id);

CREATE INDEX IF NOT EXISTS idx_audit_api_logs_transaction_id
    ON public.audit_api_logs (transaction_id);

CREATE INDEX IF NOT EXISTS idx_audit_api_logs_tenant_path_created
    ON public.audit_api_logs (tenant_id, api_path, created_at DESC);

DO `$`$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$DbUser') THEN
        EXECUTE format('ALTER ROLE %I WITH PASSWORD %L', '$DbUser', '$DbPassword');
    ELSE
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', '$DbUser', '$DbPassword');
    END IF;
END
`$`$;

GRANT USAGE ON SCHEMA public TO $DbUser;
GRANT CREATE ON SCHEMA public TO $DbUser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO $DbUser;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO $DbUser;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $DbUser;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO $DbUser;

COMMIT;
"@

try {
    Set-Content -Path $sqlFile -Value $sql -Encoding UTF8
    Write-MasterDbBootstrapLog "Wrote master DB SQL file. sqlFile=$sqlFile sizeBytes=$((Get-Item $sqlFile).Length)"

    Invoke-PsqlCommand `
        -Arguments @(
            "-h", $DbHost,
            "-p", "$DbPort",
            "-U", $DbLoginUser,
            "-d", $Database,
            "-v", "ON_ERROR_STOP=1",
            "-f", $sqlFile
        ) `
        -StepName "master-db-schema"

    Write-MasterDbBootstrapLog "Master DB bootstrap completed successfully."
} finally {
    if (Test-Path $sqlFile) {
        Remove-Item $sqlFile -Force
        Write-MasterDbBootstrapLog "Removed temporary SQL file. sqlFile=$sqlFile"
    }
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
