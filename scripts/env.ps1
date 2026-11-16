$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
    $localJdk = Get-ChildItem (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $localJdk) { throw '请安装 JDK 21 并设置 JAVA_HOME，或解压到 .tools/jdk-21*。' }
    $env:JAVA_HOME = $localJdk.FullName
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
