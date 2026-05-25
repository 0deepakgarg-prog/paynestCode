param(
    [string]$DbHost = "192.168.29.123",
    [int]$DbPort = 5432,
    [string]$Database = "paynestdb",
    [AllowEmptyString()]
    [string]$DbLoginUser = "",
    [AllowEmptyString()]
    [string]$DatabasePwd = "",
    [string]$DbUser = "appuser",
    [string]$DbPassword = "appuser",
    [string]$TenantId = "e2e",
    [string]$TenantName = "Default E2E Test Tenant",
    [string]$TenantSchema = "tenant_$TenantId",
    [string]$TenantTimeZone = "UTC"
)

$scriptPath = Join-Path $PSScriptRoot "bootstrap-paynest-db.ps1"
$EffectiveDbLoginUser = if ([string]::IsNullOrWhiteSpace($DbLoginUser)) { $DbUser } else { $DbLoginUser }
$EffectiveDatabasePwd = if ([string]::IsNullOrWhiteSpace($DatabasePwd)) { $DbPassword } else { $DatabasePwd }
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
Write-Host "[$timestamp] [db-bootstrap-wrapper] Starting PayNest DB bootstrap. dbHost=$DbHost dbPort=$DbPort database=$Database dbLoginUser=$EffectiveDbLoginUser dbUser=$DbUser tenantId=$TenantId tenantSchema=$TenantSchema script=$scriptPath"

& $scriptPath `
    -DbHost $DbHost `
    -DbPort $DbPort `
    -Database $Database `
    -DbLoginUser $EffectiveDbLoginUser `
    -DatabasePwd $EffectiveDatabasePwd `
    -DbUser $DbUser `
    -DbPassword $DbPassword `
    -TenantId $TenantId `
    -TenantName $TenantName `
    -TenantSchema $TenantSchema `
    -TenantTimeZone $TenantTimeZone

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
Write-Host "[$timestamp] [db-bootstrap-wrapper] PayNest DB bootstrap finished. tenantId=$TenantId tenantSchema=$TenantSchema exitCode=$LASTEXITCODE"

exit $LASTEXITCODE
