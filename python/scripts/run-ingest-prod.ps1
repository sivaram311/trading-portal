<#
.SYNOPSIS
    Run the trading-portal Python ingest worker against PROD (G:, schema `prod`).

.DESCRIPTION
    Loads Postgres credentials from the MyAgent secrets SoT
    (E:\MyAgent\workflow\db\secrets\postgres.env), sets INGEST_ENV=prod,
    activates the local venv (creating it on first run) and invokes
    `python -m trading_portal_ingest <mode>`.

    Run only from the PROD env root per machine rules (G:).

.PARAMETER Mode
    seed | mt5 | bootstrap-db | health-server | check-mt5   (default: seed)

.EXAMPLE
    .\scripts\run-ingest-prod.ps1
    .\scripts\run-ingest-prod.ps1 -Mode mt5 -ExtraArgs '--daemon --health'
    .\scripts\run-ingest-prod.ps1 -Mode check-mt5
#>
param(
    [ValidateSet('seed', 'mt5', 'bootstrap-db', 'health-server', 'check-mt5')]
    [string]$Mode = 'seed',
    [string]$ExtraArgs = ''
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$SecretsFile = 'E:\MyAgent\workflow\db\secrets\postgres.env'

if (-not (Test-Path $SecretsFile)) {
    Write-Error "Secrets file not found: $SecretsFile"
    exit 1
}

Write-Host "Loading Postgres secrets from $SecretsFile" -ForegroundColor Cyan
Get-Content $SecretsFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $idx = $line.IndexOf('=')
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    Set-Item -Path "Env:$key" -Value $value
}

$env:INGEST_ENV = 'prod'
if (-not $env:INGEST_SYMBOL) { $env:INGEST_SYMBOL = 'XAUUSD' }
if (-not $env:INGEST_TIMEFRAMES) { $env:INGEST_TIMEFRAMES = 'M1,M5,M15,H1,H4,D1' }
if (-not $env:INGEST_HEALTH_PORT) { $env:INGEST_HEALTH_PORT = '5342' }

$VenvPython = Join-Path $RepoRoot '.venv\Scripts\python.exe'
if (-not (Test-Path $VenvPython)) {
    Write-Host "Creating venv at $RepoRoot\.venv" -ForegroundColor Cyan
    python -m venv (Join-Path $RepoRoot '.venv')
    & $VenvPython -m pip install --upgrade pip -q
    & $VenvPython -m pip install -r (Join-Path $RepoRoot 'requirements.txt') -q
}

Push-Location $RepoRoot
try {
    Write-Host "Running: python -m trading_portal_ingest $Mode $ExtraArgs (env=prod, schema=prod)" -ForegroundColor Green
    if ($ExtraArgs) {
        & $VenvPython -m trading_portal_ingest $Mode $ExtraArgs.Split(' ')
    } else {
        & $VenvPython -m trading_portal_ingest $Mode
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
