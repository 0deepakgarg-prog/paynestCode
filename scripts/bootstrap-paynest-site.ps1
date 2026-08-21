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
    [string]$TenantSchema = "",
    [string]$TenantTimeZone = "UTC",
    [string]$DefaultAdminAccountId = "ADMIN0000000001",
    [string]$DefaultAdminLoginId = "superadmin",
    [string]$DefaultAdminPassword = "Admin@123",
    [string]$DefaultAdminEmail = "superadmin@paynest.local",
    [string]$DefaultAdminMobile = "+10000000000",
    [string]$DefaultAdminAuthSalt = "paynest-default-admin-salt"
)

$ErrorActionPreference = "Stop"

function Write-SiteBootstrapLog {
    param([string]$Message)

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
    Write-Host "[$timestamp] [site-bootstrap] $Message"
}

if ([string]::IsNullOrWhiteSpace($TenantSchema)) {
    $TenantSchema = "tenant_$TenantId"
}

$masterBootstrap = Join-Path $PSScriptRoot "bootstrap-paynest-master-db.ps1"
$tenantBootstrap = Join-Path $PSScriptRoot "bootstrap-paynest-db.ps1"

if (-not (Test-Path $masterBootstrap)) {
    throw "Master bootstrap script not found: $masterBootstrap"
}

if (-not (Test-Path $tenantBootstrap)) {
    throw "Tenant bootstrap script not found: $tenantBootstrap"
}

Write-SiteBootstrapLog "Starting PayNest PostgreSQL site bootstrap. database=$Database tenantId=$TenantId tenantSchema=$TenantSchema appUser=$DbUser"

Write-SiteBootstrapLog "Running public/master schema bootstrap."
& $masterBootstrap `
    -DbHost $DbHost `
    -DbPort $DbPort `
    -Database $Database `
    -DbLoginUser $DbLoginUser `
    -DatabasePwd $DatabasePwd `
    -DbUser $DbUser `
    -DbPassword $DbPassword

Write-SiteBootstrapLog "Running tenant schema bootstrap."
& $tenantBootstrap `
    -DbHost $DbHost `
    -DbPort $DbPort `
    -Database $Database `
    -DbLoginUser $DbLoginUser `
    -DatabasePwd $DatabasePwd `
    -DbUser $DbUser `
    -DbPassword $DbPassword `
    -TenantId $TenantId `
    -TenantName $TenantName `
    -TenantSchema $TenantSchema `
    -TenantTimeZone $TenantTimeZone `
    -DefaultAdminAccountId $DefaultAdminAccountId `
    -DefaultAdminLoginId $DefaultAdminLoginId `
    -DefaultAdminPassword $DefaultAdminPassword `
    -DefaultAdminEmail $DefaultAdminEmail `
    -DefaultAdminMobile $DefaultAdminMobile `
    -DefaultAdminAuthSalt $DefaultAdminAuthSalt

Write-SiteBootstrapLog "PayNest PostgreSQL site bootstrap completed. database=$Database tenantId=$TenantId tenantSchema=$TenantSchema appUser=$DbUser"
