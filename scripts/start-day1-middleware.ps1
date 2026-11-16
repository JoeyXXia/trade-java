param([switch]$NacosOnly)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
Set-Location $projectRoot
$toolsRoot = (Resolve-Path '.tools').Path
if (Test-Path '.tools/day1-processes.json') {
    foreach ($entry in (Get-Content -Raw '.tools/day1-processes.json' | ConvertFrom-Json)) {
        $existing = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($existing -and $existing.StartTime.ToUniversalTime().Ticks -eq ([datetime]$entry.startTime).ToUniversalTime().Ticks) {
            throw '已有 Day 1 中间件进程，请先运行 scripts/stop-day1-middleware.ps1。'
        }
    }
}
$javaExe = Join-Path $env:JAVA_HOME 'bin/java.exe'
$runtime = Join-Path $toolsRoot 'runtime'
New-Item -ItemType Directory -Force $runtime | Out-Null
$nacosRoot = Join-Path $toolsRoot 'nacos'
if (-not (Test-Path "$nacosRoot/target/nacos-server.jar")) {
    throw '请将 Nacos 3.0.3 发行包解压到 .tools/nacos。'
}
# 随机身份只保存于被忽略的本地发行包，不提交测试密钥。
$configPath = Join-Path $nacosRoot 'conf/application.properties'
$config = Get-Content -Raw -LiteralPath $configPath
$config = $config -replace '(?m)^nacos.core.auth.server.identity.key=.*', ('nacos.core.auth.server.identity.key=day1-' + [guid]::NewGuid().ToString('N'))
$config = $config -replace '(?m)^nacos.core.auth.server.identity.value=.*', ('nacos.core.auth.server.identity.value=' + [guid]::NewGuid().ToString('N'))
$config = $config -replace '(?m)^nacos.core.auth.plugin.nacos.token.secret.key=.*', ('nacos.core.auth.plugin.nacos.token.secret.key=' + [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)))
Set-Content -LiteralPath $configPath -Value $config -Encoding utf8
$processes = @()
function Start-ProbeProcess($name, $arguments) {
    $process = Start-Process -FilePath $javaExe -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$toolsRoot/$name.out.log" -RedirectStandardError "$toolsRoot/$name.err.log"
    return @{ name = $name; id = $process.Id; startTime = $process.StartTime.ToUniversalTime().ToString('o') }
}
$processes += Start-ProbeProcess 'nacos' @('-Xms128m', '-Xmx384m', "-Duser.home=$runtime", '-Dnacos.standalone=true', "-Dnacos.home=$nacosRoot", '-jar', "$nacosRoot/target/nacos-server.jar", "--spring.config.additional-location=file:$nacosRoot/conf/", '--nacos.deployment.type=merged', '--nacos.console.port=18880', '--nacos.core.auth.enabled=false', '--nacos.core.auth.console.enabled=false', '--server.address=127.0.0.1')
if (-not $NacosOnly) {
    $mqRoot = Join-Path $toolsRoot 'rocketmq-all-5.3.1-bin-release'
    if (-not (Test-Path "$mqRoot/lib")) { throw '请先解压 RocketMQ 5.3.1 到 .tools/rocketmq-all-5.3.1-bin-release。' }
    $brokerConfig = Join-Path $runtime 'broker.conf'
    @"
brokerClusterName=Day1Cluster
brokerName=day1-broker
brokerId=0
brokerIP1=127.0.0.1
namesrvAddr=127.0.0.1:9876
listenPort=10911
autoCreateTopicEnable=true
storePathRootDir=.tools/runtime/rocketmq-store
mappedFileSizeCommitLog=16777216
mappedFileSizeConsumeQueue=300000
diskMaxUsedSpaceRatio=99
"@ | Set-Content -LiteralPath $brokerConfig -Encoding ascii
    $processes += Start-ProbeProcess 'namesrv' @('-Xms64m', '-Xmx128m', "-Duser.home=$runtime", "-Drocketmq.home.dir=$mqRoot", '-cp', "$mqRoot/lib/*", 'org.apache.rocketmq.namesrv.NamesrvStartup')
    $processes += Start-ProbeProcess 'broker' @('-Xms128m', '-Xmx256m', "-Duser.home=$runtime", "-Drocketmq.home.dir=$mqRoot", '--add-opens=java.base/java.lang=ALL-UNNAMED', '--add-opens=java.base/java.nio=ALL-UNNAMED', '-cp', "$mqRoot/lib/*", 'org.apache.rocketmq.broker.BrokerStartup', '-c', $brokerConfig)
}
$processes | ConvertTo-Json -AsArray | Set-Content .tools/day1-processes.json
$processes | ForEach-Object { Write-Output "$($_.name): PID $($_.id)" }
Write-Output '仅本地隔离实验，未启用客户端鉴权，不可用于公网；检查 .tools/*.out.log 确认就绪后再跑 live 测试。'
