#Requires -Version 7.0
param([ValidateRange(1, 2)][int]$Runs = 2)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
$docker = Get-Command docker -ErrorAction SilentlyContinue
$dockerExecutable = if ($docker) { $docker.Source } else {
    @('C:/Program Files/Docker/Docker/resources/bin/docker.exe',
      (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe')) |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $dockerExecutable) {
    throw '未找到 Docker CLI。请安装并启动 Docker Desktop（Linux 容器）后重试；集成测试未运行。'
}
if (-not (Test-Path -LiteralPath $dockerExecutable)) {
    throw '未找到 Docker CLI。请安装并启动 Docker Desktop（Linux 容器）后重试；集成测试未运行。'
}
$env:PATH = "$(Split-Path $dockerExecutable);$env:PATH"
$engine = & $dockerExecutable info --format '{{.OSType}}'
if ($LASTEXITCODE -ne 0 -or ($engine | Out-String).Trim() -ne 'linux') {
    throw 'Docker Linux 引擎未就绪，未运行集成测试。'
}
# No developer database URL or password is passed to tests.
$originalDirectory = Get-Location
try {
    Set-Location $projectRoot
    New-Item -ItemType Directory -Force docs/reports/day2 | Out-Null
    for ($attempt = 1; $attempt -le $Runs; $attempt++) {
        & "$projectRoot/mvnw.cmd" -B -ntp -Pintegration verify 2>&1 |
            Tee-Object "docs/reports/day2/integration-run-$attempt.txt"
        if ($LASTEXITCODE -ne 0) { throw "集成测试第 $attempt 次失败，参见对应报告；未记为通过。" }
    }
} finally {
    Set-Location $originalDirectory
}
