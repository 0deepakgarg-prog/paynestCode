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
    [string]$TenantTimeZone = "UTC"
)

$scriptPath = Join-Path $PSScriptRoot "bootstrap-paynest-db.ps1"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
Write-Host "[$timestamp] [db-bootstrap-wrapper] Starting PayNest DB bootstrap. dbHost=$DbHost dbPort=$DbPort database=$Database dbLoginUser=$DbLoginUser dbUser=$DbUser tenantId=$TenantId tenantSchema=$TenantSchema script=$scriptPath"

& $scriptPath `
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
    -TenantTimeZone $TenantTimeZone

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
Write-Host "[$timestamp] [db-bootstrap-wrapper] PayNest DB bootstrap finished. tenantId=$TenantId tenantSchema=$TenantSchema exitCode=$LASTEXITCODE"

exit $LASTEXITCODE
