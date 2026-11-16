$ErrorActionPreference = 'Stop'
$statePath = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools/day1-processes.json'
if (-not (Test-Path -LiteralPath $statePath)) { return }
foreach ($entry in (Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json)) {
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq ([datetime]$entry.startTime).ToUniversalTime().Ticks) {
        Stop-Process -Id $entry.id
        Write-Output "Stopped $($entry.name)"
    }
}
