# run-ui-dev.ps1 — start the Trading Portal UI dev server on DEV port 3341.
# Usage:  powershell -ExecutionPolicy Bypass -File .\scripts\run-ui-dev.ps1
#
# Notes:
#  - DEV port 3341 is reserved for trading-portal in MyAgent workflow/ports/REGISTRY.md.
#  - API base is http://127.0.0.1:3340; CSS DEV IdP is http://127.0.0.1:9000.
#  - If the backend/CSS are down, the UI degrades gracefully (mock banner + optional DEV_TOKEN).

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$Port = if ($env:TP_UI_PORT) { $env:TP_UI_PORT } else { '3341' }

Write-Host "Trading Portal UI — DEV" -ForegroundColor Yellow
Write-Host ("  root : {0}" -f $projectRoot)
Write-Host ("  port : {0}" -f $Port)
Write-Host  "  api  : http://127.0.0.1:3340"
Write-Host  "  css  : http://127.0.0.1:9000  (clientId=trading-portal)"
Write-Host ""

if (-not (Test-Path (Join-Path $projectRoot 'node_modules'))) {
  Write-Host "node_modules missing — running npm install…" -ForegroundColor Cyan
  npm install
}

Write-Host "Starting ng serve on 127.0.0.1:$Port …" -ForegroundColor Green
npm run start -- --port $Port
