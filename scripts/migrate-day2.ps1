#Requires -Version 7.0
param(
    [ValidateSet('migrate', 'validate', 'info')]
    [string]$Action = 'migrate',
    [string]$EnvFile
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot 'deploy/.env' }
if (-not (Test-Path -LiteralPath $EnvFile)) { throw '缺少环境文件，请先运行 scripts/init-day2-env.ps1。' }
$config = @{}
foreach ($line in (Get-Content -LiteralPath $EnvFile)) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $config[$Matches[1]] = $Matches[2].Trim() }
}
if (-not $config['TRADE_MIGRATION_PASSWORD'] -or $config['TRADE_MIGRATION_PASSWORD'] -notmatch '^[a-fA-F0-9]{64}$') {
    throw 'TRADE_MIGRATION_PASSWORD 缺失或格式错误；未连接数据库。'
}
if (-not $config['MYSQL_PORT'] -or $config['MYSQL_PORT'] -notmatch '^\d{1,5}$' -or [int]$config['MYSQL_PORT'] -lt 1 -or [int]$config['MYSQL_PORT'] -gt 65535) {
    throw 'MYSQL_PORT 缺失或无效；未连接数据库。'
}
$previous = @{}
foreach ($name in @('FLYWAY_URL', 'FLYWAY_USER', 'FLYWAY_PASSWORD')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$originalDirectory = Get-Location
try {
    Set-Location $projectRoot
    $env:FLYWAY_URL = "jdbc:mysql://127.0.0.1:$($config['MYSQL_PORT'])/commerceflow_trade?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000"
    $env:FLYWAY_USER = 'trade_migrator'
    $env:FLYWAY_PASSWORD = $config['TRADE_MIGRATION_PASSWORD']
    & "$projectRoot/mvnw.cmd" -B -ntp -pl backend/trade-service "flyway:$Action"
    if ($LASTEXITCODE -ne 0) { throw "Flyway $Action 失败（exit $LASTEXITCODE）；未自动 repair、clean 或重建数据库。" }
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Set-Location $originalDirectory
}
