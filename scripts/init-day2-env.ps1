#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$destination = Join-Path (Split-Path $PSScriptRoot -Parent) 'deploy/.env'
if (Test-Path -LiteralPath $destination) {
    throw 'deploy/.env 已存在，未覆盖。已有数据卷的账号口令不会随 env 文件自动改变。'
}
function New-LocalPassword {
    [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
}
@"
# Local-only credentials; generated, never committed.
MYSQL_PORT=13306
REDIS_PORT=16379
MYSQL_ROOT_PASSWORD=$(New-LocalPassword)
TRADE_MIGRATION_PASSWORD=$(New-LocalPassword)
TRADE_APP_PASSWORD=$(New-LocalPassword)
REDIS_PASSWORD=$(New-LocalPassword)
"@ | Set-Content -LiteralPath $destination -Encoding utf8NoBOM
Write-Output '已生成 deploy/.env，未输出口令。不要提交或分享该文件。'
