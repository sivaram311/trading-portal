#Requires -Version 5.1
<#
.SYNOPSIS
  Paper-only backtest sweep harness for the open calibration tasks in
  docs/calibration/OPEN-CALIBRATION.md (DEEP-ALGORITHMS §14).

.DESCRIPTION
  Calls the existing POST /api/backtest/run endpoint (optionally with walk-forward /
  Monte-Carlo enabled) across every combination of -Styles x -MaxBarsList, against a
  local DEV backend. Never places or simulates a live order — /api/backtest/run is
  paper/stored-OHLC only (see BacktestController.java).

  IMPORTANT — current API surface: POST /api/backtest/run only accepts maxBars, style,
  walkForward, monteCarlo, mcIterations. It does NOT (yet) accept overrides for
  time_scale, So9 steps, confluence weights, spread/slippage, or equal_eps_pts — those
  are compiled into GannConfig/IctConfig/BacktestConfig/ConfluenceEngine defaults (see
  docs/calibration/DEFAULTS-v0.3.md). This script sweeps what the API supports today
  (style x bar-window x walk-forward) so you get a real, safe baseline. Sweeping the
  engine-level parameters requires either temporary local edits to the relevant config
  class (rerun, revert) or a follow-up PR that threads params through BacktestConfig —
  this script deliberately does not add that follow-up so engine defaults cannot drift
  as a side effect of running a "calibration script".

  This script NEVER changes engine defaults, NEVER enables live execution, and NEVER
  writes to the backend other than calling the existing paper-backtest endpoint.

.PARAMETER BaseUrl
  Backend base URL. Default http://127.0.0.1:3340 (DEV port per workflow/ports/REGISTRY.md).

.PARAMETER Styles
  Trading styles to sweep. Default: SCALP, DAY, POSITIONAL.

.PARAMETER MaxBarsList
  M15 bar-window sizes to sweep (bounded server-side to [80, 2000]). Default: 200, 400, 800.

.PARAMETER WalkForward
  Also request walk-forward aggregation on each run (adds wf.* fields to output).

.PARAMETER MonteCarlo
  Also request Monte-Carlo resampling on each run (adds mc.* fields to output).

.PARAMETER McIterations
  Monte-Carlo iteration count when -MonteCarlo is set. Default 200.

.PARAMETER DevToken
  Value sent as X-Dev-Token when -UseDevBypass is set. Default matches
  application-dev.properties trading.security.dev-token.

.PARAMETER UseDevBypass
  Send the X-Dev-Token header (DEV-only fixed-header auth; requires
  trading.security.dev-bypass=true on the target backend). Omit if the backend uses
  real CSS JWKS and you pass -BearerToken instead.

.PARAMETER BearerToken
  Optional CSS bearer token, sent as Authorization: Bearer <token> instead of the dev
  bypass header.

.PARAMETER DryRun
  Print the planned requests (method, URL, query params) and exit without calling the
  backend or checking reachability. Safe to run with no backend up at all.

.PARAMETER OutCsv
  Optional path to write a CSV of all run results (one row per style/maxBars/mode
  combination). Directory is created if missing.

.PARAMETER TimeoutSec
  Per-request timeout. Default 60 (walk-forward/Monte-Carlo runs can be slower).

.EXAMPLE
  # See exactly what would be called, no backend required.
  powershell -File scripts\calibration\run-weight-sweep.ps1 -DryRun

.EXAMPLE
  # Real sweep against a local DEV backend with dev-bypass auth enabled.
  powershell -File scripts\calibration\run-weight-sweep.ps1 -UseDevBypass -WalkForward

.EXAMPLE
  # Narrow sweep, single style, write results to CSV for the OPEN-CALIBRATION.md log.
  powershell -File scripts\calibration\run-weight-sweep.ps1 -UseDevBypass -Styles DAY `
    -MaxBarsList 400 -WalkForward -MonteCarlo -OutCsv docs\calibration\results\2026-07-30-day.csv

.NOTES
  No live trading. Reads-only against stored OHLC via the backend; does not touch
  trading.exec.* flags. See docs/calibration/OPEN-CALIBRATION.md for what to do with
  the results before proposing any default change.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:3340',
    [ValidateSet('SCALP', 'DAY', 'POSITIONAL')]
    [string[]]$Styles = @('SCALP', 'DAY', 'POSITIONAL'),
    [int[]]$MaxBarsList = @(200, 400, 800),
    [switch]$WalkForward,
    [switch]$MonteCarlo,
    [int]$McIterations = 200,
    [string]$DevToken = 'dev-operator-token',
    [switch]$UseDevBypass,
    [string]$BearerToken = '',
    [switch]$DryRun,
    [string]$OutCsv = '',
    [int]$TimeoutSec = 60
)

$ErrorActionPreference = 'Stop'

function Get-AuthHeaders {
    if ($BearerToken) {
        return @{ Authorization = "Bearer $BearerToken" }
    }
    if ($UseDevBypass) {
        return @{ 'X-Dev-Token' = $DevToken }
    }
    return @{}
}

function Test-Backend([string]$url) {
    try {
        $h = Invoke-RestMethod -Uri "$url/api/health" -TimeoutSec 6
        return @{ reachable = $true; status = $h.status }
    } catch {
        return @{ reachable = $false; status = $_.Exception.Message }
    }
}

$headers = Get-AuthHeaders
$plan = New-Object System.Collections.Generic.List[hashtable]
foreach ($style in $Styles) {
    foreach ($maxBars in $MaxBarsList) {
        $plan.Add(@{
            style      = $style
            maxBars    = $maxBars
            walkForward = [bool]$WalkForward
            monteCarlo  = [bool]$MonteCarlo
            mcIterations = $McIterations
        })
    }
}

Write-Host "Calibration sweep plan: $($plan.Count) run(s) against $BaseUrl" -ForegroundColor Cyan
foreach ($p in $plan) {
    $qs = "maxBars=$($p.maxBars)&style=$($p.style)&walkForward=$($p.walkForward.ToString().ToLower())&monteCarlo=$($p.monteCarlo.ToString().ToLower())&mcIterations=$($p.mcIterations)"
    Write-Host ("  POST {0}/api/backtest/run?{1}" -f $BaseUrl, $qs)
}

if ($DryRun) {
    Write-Host "`n-DryRun set: no requests sent, no reachability check performed." -ForegroundColor Yellow
    return
}

$probe = Test-Backend $BaseUrl
if (-not $probe.reachable) {
    Write-Warning "Backend not reachable at $BaseUrl/api/health ($($probe.status))."
    Write-Warning "Start it first, e.g.: powershell -File scripts\run-api-dev.ps1"
    Write-Warning "Or re-run with -DryRun to preview the sweep without a live backend."
    exit 1
}
Write-Host "Backend reachable (status=$($probe.status)). Running sweep...`n" -ForegroundColor Green

$results = New-Object System.Collections.Generic.List[pscustomobject]
foreach ($p in $plan) {
    $uri = "$BaseUrl/api/backtest/run?maxBars=$($p.maxBars)&style=$($p.style)&walkForward=$($p.walkForward.ToString().ToLower())&monteCarlo=$($p.monteCarlo.ToString().ToLower())&mcIterations=$($p.mcIterations)"
    Write-Host "-> $($p.style) maxBars=$($p.maxBars) walkForward=$($p.walkForward) monteCarlo=$($p.monteCarlo)"
    try {
        $r = Invoke-RestMethod -Method POST -Uri $uri -Headers $headers -TimeoutSec $TimeoutSec
        $row = [pscustomobject]@{
            style               = $r.style
            max_bars_requested  = $p.maxBars
            m15_bars_used       = $r.m15_bars_used
            trade_count         = $r.trade_count
            win_rate            = $r.win_rate
            profit_factor       = $r.profit_factor
            expectancy_r        = $r.expectancy_r
            max_drawdown_pct    = $r.max_drawdown_pct
            total_r             = $r.total_r
            wf_folds            = if ($r.walk_forward) { $r.walk_forward.folds } else { $null }
            wf_expectancy_r     = if ($r.walk_forward) { $r.walk_forward.expectancy_r } else { $null }
            wf_profit_factor    = if ($r.walk_forward) { $r.walk_forward.profit_factor } else { $null }
            wf_max_drawdown_pct = if ($r.walk_forward) { $r.walk_forward.max_drawdown_pct } else { $null }
            mc_p5_expectancy_r  = if ($r.monte_carlo) { $r.monte_carlo.p5_expectancy_r } else { $null }
            mc_p50_expectancy_r = if ($r.monte_carlo) { $r.monte_carlo.p50_expectancy_r } else { $null }
            mc_p95_expectancy_r = if ($r.monte_carlo) { $r.monte_carlo.p95_expectancy_r } else { $null }
            error               = $null
        }
    } catch {
        $row = [pscustomobject]@{
            style = $p.style; max_bars_requested = $p.maxBars; m15_bars_used = $null
            trade_count = $null; win_rate = $null; profit_factor = $null; expectancy_r = $null
            max_drawdown_pct = $null; total_r = $null; wf_folds = $null; wf_expectancy_r = $null
            wf_profit_factor = $null; wf_max_drawdown_pct = $null; mc_p5_expectancy_r = $null
            mc_p50_expectancy_r = $null; mc_p95_expectancy_r = $null
            error = $_.Exception.Message
        }
        Write-Warning "  FAILED: $($_.Exception.Message)"
    }
    $results.Add($row)
}

Write-Host "`n=== Sweep results ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize

if ($OutCsv) {
    $dir = Split-Path $OutCsv -Parent
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $results | Export-Csv -Path $OutCsv -NoTypeInformation -Encoding utf8
    Write-Host "Wrote $OutCsv"
}

Write-Host "`nReminder: this only sweeps style/bar-window/walk-forward (current API surface)." -ForegroundColor DarkYellow
Write-Host "See docs/calibration/OPEN-CALIBRATION.md for engine-level params (time_scale, So9" -ForegroundColor DarkYellow
Write-Host "steps, confluence weights, slippage) that still need a config-level sweep." -ForegroundColor DarkYellow
