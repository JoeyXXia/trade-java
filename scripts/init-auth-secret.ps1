#Requires -Version 7.0
$ErrorActionPreference='Stop'
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) 'deploy/.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw '请先生成 deploy/.env。' }
if (Select-String -LiteralPath $envFile -Pattern '^JWT_SECRET_BASE64=') {
    Write-Output 'JWT密钥配置已存在，未覆盖。'
    return
}
$value = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
Add-Content -LiteralPath $envFile -Value "`nJWT_SECRET_BASE64=$value" -Encoding utf8NoBOM
Write-Output 'JWT密钥已生成到忽略的 deploy/.env，未输出密钥。'
