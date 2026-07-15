<#
.SYNOPSIS
  Start the Trading Portal backend API on DEV (port 3340, profile `dev`).

.DESCRIPTION
  Loads the DEV Postgres password from the machine secrets file
  (E:\MyAgent\workflow\db\secrets\postgres.env) into environment variables so no secret needs to
  live in a git-tracked file, then launches Spring Boot with the `dev` profile.

  DEV auth: `trading.security.dev-bypass=true` (set in application-dev.properties) — protected
  endpoints accept the fixed header `X-Dev-Token: dev-operator-token`. CSS JWKS validation is the
  default (non-DEV) path; enable it by setting dev-bypass=false once CSS :9000 is reachable.

.EXAMPLE
  powershell -File scripts\run-api-dev.ps1
#>
[CmdletBinding()]
param(
    [string]$SecretsFile = 'E:\MyAgent\workflow\db\secrets\postgres.env'
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot '..\backend'

# Load DEV DB credentials from the secrets file (never committed).
if (Test-Path $SecretsFile) {
    Get-Content $SecretsFile | ForEach-Object {
        if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.+?)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2]
            if ($name -in @('TRADING_PORTAL_ROLE_DEV', 'TRADING_PORTAL_ROLE_DEV_PASSWORD')) {
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            }
        }
    }
    Write-Host "Loaded DEV DB credentials from $SecretsFile"
} else {
    Write-Warning "Secrets file not found: $SecretsFile. Falling back to application-local.properties if present."
}

Write-Host "Starting Trading Portal API on http://127.0.0.1:3340 (profile=dev)..."
Push-Location $backend
try {
    mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.profiles=dev"
} finally {
    Pop-Location
}
