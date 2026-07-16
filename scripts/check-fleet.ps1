#Requires -Version 5.1
<#
.SYNOPSIS
  Trading Portal fleet observability smoke (DEV / PREPROD / PROD).

.DESCRIPTION
  Roadmap 0.3 track 3+4: probes API health, ingest health, optional /api/ops/status
  (CSS Bearer), and shared PG connection checker. Paper-only — no live orders.

.EXAMPLE
  powershell -File scripts\check-fleet.ps1
  powershell -File scripts\check-fleet.ps1 -WithAuth
#>
param(
  [switch]$WithAuth,
  [string]$OutFile = ''
)

$ErrorActionPreference = 'Continue'
$envs = @(
  @{ name='dev';     api=3340; ingest=3342; css='http://127.0.0.1:9000/auth/login' },
  @{ name='preprod'; api=4340; ingest=4342; css='http://127.0.0.1:4910/auth/login' },
  @{ name='prod';    api=5340; ingest=5342; css='http://127.0.0.1:5910/auth/login' }
)

$adminPass = $null
if ($WithAuth) {
  foreach ($p in @('F:\apps\css\.env','G:\apps\css\.env')) {
    if (Test-Path $p) {
      Get-Content $p | ForEach-Object {
        if ($_ -match '^\s*CSS_ADMIN_PASSWORD=(.+)$') { $adminPass = $Matches[1].Trim().Trim('"') }
      }
      if ($adminPass) { break }
    }
  }
  if (-not $adminPass) { Write-Warning "No CSS_ADMIN_PASSWORD found; skipping auth probes" }
}

function Get-CssToken([string]$loginUrl) {
  if (-not $adminPass) { return $null }
  try {
    $body = @{ username='admin'; password=$adminPass; clientId='trading-portal' } | ConvertTo-Json
    $login = Invoke-RestMethod -Method POST -Uri $loginUrl -ContentType 'application/json' -Body $body -TimeoutSec 15
    $t = $login.accessToken; if (-not $t) { $t = $login.access_token }
    return $t
  } catch {
    Write-Warning "CSS login failed ($loginUrl): $($_.Exception.Message)"
    return $null
  }
}

$lines = New-Object System.Collections.Generic.List[string]
function Add-Line([string]$s) { $lines.Add($s); Write-Host $s }

Add-Line ("checked_at=" + (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK'))

foreach ($e in $envs) {
  $apiUrl = "http://127.0.0.1:$($e.api)/api/health"
  $ingUrl = "http://127.0.0.1:$($e.ingest)/health"
  $api = 'DOWN'; $ing = 'DOWN'; $ops = 'n/a'
  try {
    $h = Invoke-RestMethod -Uri $apiUrl -TimeoutSec 6
    $api = "$($h.status) db=$($h.checks.db) ingest=$($h.checks.ingest)"
  } catch { $api = "FAIL $($_.Exception.Message)" }
  try {
    $ih = Invoke-RestMethod -Uri $ingUrl -TimeoutSec 6
    $ing = "$($ih.status) mode=$($ih.ingest.last_mode) db=$($ih.db.ok)"
  } catch { $ing = "DOWN/unreachable" }
  if ($WithAuth -and $adminPass) {
    $token = Get-CssToken $e.css
    if ($token) {
      try {
        $hdr = @{ Authorization = "Bearer $token" }
        $st = Invoke-RestMethod -Headers $hdr -Uri "http://127.0.0.1:$($e.api)/api/ops/status" -TimeoutSec 30
        $ops = "level=$($st.level) soak_met=$($st.soak.soak_met) decisions=$($st.soak.journal_decision_count) live=$($st.live_enabled) ingest_ok=$($st.ingest.reachable)"
      } catch {
        try {
          $hdr = @{ Authorization = "Bearer $token" }
          $sk = Invoke-RestMethod -Headers $hdr -Uri "http://127.0.0.1:$($e.api)/api/ops/soak" -TimeoutSec 10
          $ops = "soak_met=$($sk.soak_met) decisions=$($sk.journal_decision_count) (no /status)"
        } catch { $ops = "ops FAIL" }
      }
    } else { $ops = 'auth FAIL' }
  }
  Add-Line ("[$($e.name)] api=:$($e.api) {$api} | ingest=:$($e.ingest) {$ing} | ops={$ops}")
}

if ($WithAuth -and $adminPass) {
  $tok = Get-CssToken 'http://127.0.0.1:4910/auth/login'
  if ($tok) {
    $hdr = @{ Authorization = "Bearer $tok" }
    try {
      $d = Invoke-RestMethod -Headers $hdr -Uri 'http://127.0.0.1:4340/api/confluence/decision' -TimeoutSec 20
      Add-Line ("[preprod-paper] decision grade=$($d.grade) mode=$($d.mode) dir=$($d.direction) id=$($d.id)")
    } catch { Add-Line "[preprod-paper] decision FAIL $($_.Exception.Message)" }
    try {
      $j = Invoke-RestMethod -Headers $hdr -Uri 'http://127.0.0.1:4340/api/paper/journal?limit=5' -TimeoutSec 15
      $n = if ($j.items) { $j.items.Count } else { '?' }
      Add-Line "[preprod-paper] journal ok items=$n total=$($j.total)"
    } catch { Add-Line "[preprod-paper] journal FAIL $($_.Exception.Message)" }
    try {
      $r = Invoke-RestMethod -Method POST -Headers $hdr -Uri 'http://127.0.0.1:4340/api/ops/replay' -TimeoutSec 45
      Add-Line ("[preprod-paper] replay ok grade=$($r.grade) id=$($r.id)")
    } catch { Add-Line "[preprod-paper] replay FAIL $($_.Exception.Message)" }
  }
}

try {
  $pgOut = & powershell -NoProfile -ExecutionPolicy Bypass -File 'E:\MyAgent\workflow\db\scripts\check-pg-connections.ps1' 2>&1
  Add-Line ("[pg] $pgOut")
} catch { Add-Line "[pg] FAIL $($_.Exception.Message)" }

if ($OutFile) {
  $dir = Split-Path $OutFile -Parent
  if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
  $lines | Set-Content -Path $OutFile -Encoding utf8
  Write-Host "Wrote $OutFile"
}
