#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
$config = @{}
foreach ($line in (Get-Content -LiteralPath (Join-Path $projectRoot 'deploy/.env'))) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $config[$Matches[1]] = $Matches[2].Trim() }
}
if ($config['TRADE_APP_PASSWORD'] -notmatch '^[a-fA-F0-9]{64}$' -or $config['MYSQL_PORT'] -notmatch '^\d{1,5}$') {
    throw '本地数据库配置无效；请先初始化环境、启动 Compose 并显式迁移。'
}
$previous = @{}
if (-not $config['JWT_SECRET_BASE64']) { throw '请先运行 scripts/init-auth-secret.ps1。' }
foreach ($key in @('TRADE_DB_URL','TRADE_DB_USER','TRADE_DB_PASSWORD','JWT_SECRET_BASE64')) { $previous[$key] = [Environment]::GetEnvironmentVariable($key) }
try {
    $env:TRADE_DB_URL = "jdbc:mysql://127.0.0.1:$($config['MYSQL_PORT'])/commerceflow_trade?sslMode=DISABLED&allowPublicKeyRetrieval=true"
    $env:TRADE_DB_USER = 'trade_app'
    $env:TRADE_DB_PASSWORD = $config['TRADE_APP_PASSWORD']
    $env:JWT_SECRET_BASE64 = $config['JWT_SECRET_BASE64']
    & "$env:JAVA_HOME/bin/java.exe" -jar "$projectRoot/backend/trade-service/target/trade-service-0.1.0-SNAPSHOT.jar" --spring.profiles.active=database
    if ($LASTEXITCODE -ne 0) { throw "Trade 启动/运行失败（exit $LASTEXITCODE）。" }
} finally { foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key]) } }
