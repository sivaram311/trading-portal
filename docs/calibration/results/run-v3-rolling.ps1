# Rolling-only v3 with periodic token refresh.
$ErrorActionPreference = 'Stop'
$outDir = 'E:\MyWorkspace\trading-portal\docs\calibration\results'
$api = 'http://127.0.0.1:3340'

function Get-Token {
    $cssEnv = @{}
    Get-Content 'G:\apps\css-next\.env' | ForEach-Object {
        if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.+?)\s*$') { $cssEnv[$Matches[1]] = $Matches[2].Trim('"') }
    }
    $body = @{ username = 'admin'; password = $cssEnv['CSS_ADMIN_PASSWORD']; clientId = 'trading-portal' } | ConvertTo-Json
    $login = Invoke-RestMethod -Method POST -Uri 'http://127.0.0.1:4910/auth/login' -ContentType 'application/json' -Body $body -TimeoutSec 20
    $t = $login.accessToken; if (-not $t) { $t = $login.access_token }
    if (-not $t) { throw 'login failed' }
    $t | Set-Content (Join-Path $outDir '.bearer.token') -NoNewline
    return $t
}

$token = Get-Token
$hdr = @{ Authorization = "Bearer $token" }
$caps = Invoke-RestMethod -Headers $hdr -Uri "$api/api/backtest/capabilities" -TimeoutSec 30
$m15 = [int]$caps.m15_bars_available
Write-Host "m15=$m15"

# Finish last census asofs that 401'd + spot A/A+ rows already saved; re-run full short census of 20 late samples
Write-Host '=== late census refill ==='
$lateFrom = [datetime]::Parse('2026-07-22T22:00:00Z').ToUniversalTime()
$lateTo = [datetime]::Parse('2026-07-30T18:15:00Z').ToUniversalTime()
$lateRows = @()
for ($i = 0; $i -lt 12; $i++) {
    if ($i % 6 -eq 0) { $token = Get-Token; $hdr = @{ Authorization = "Bearer $token" } }
    $t = $lateFrom.AddTicks([int64](($lateTo.Ticks - $lateFrom.Ticks) * $i / 11))
    $asof = $t.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $d = Invoke-RestMethod -Method POST -Headers $hdr -Uri "$api/api/ops/replay?asof=$asof" -TimeoutSec 90
    $lateRows += [ordered]@{
        asof = $asof; grade = [string]$d.grade; mode = [string]$d.mode; automation = [string]$d.automation
        direction = [string]$d.direction; gann_data_gap = [bool]($d.reasons -contains 'GANN_DATA_GAP'); reasons = @($d.reasons)
    }
    Write-Host ("late {0} grade={1} mode={2} gap={3}" -f $asof, $d.grade, $d.mode, ($d.reasons -contains 'GANN_DATA_GAP'))
}
$lateRows | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir 'confluence-sample-late-v3.json')

Write-Host '=== rolling ==='
$window = 1000
$step = 250
$maxEndAgo = [Math]::Max(0, $m15 - $window)
$styles = @('SCALP', 'DAY', 'POSITIONAL')
$sweep = @()
$n = 0
for ($endAgo = 0; $endAgo -le $maxEndAgo; $endAgo += $step) {
    foreach ($style in $styles) {
        if ($n % 4 -eq 0) { $token = Get-Token; $hdr = @{ Authorization = "Bearer $token" } }
        $uri = "$api/api/backtest/run?maxBars=$window&endBarsAgo=$endAgo&style=$style"
        Write-Host "run style=$style endBarsAgo=$endAgo"
        $res = Invoke-RestMethod -Method POST -Headers $hdr -Uri $uri -TimeoutSec 600
        $sweep += [ordered]@{
            style = $style; maxBars = $window; end_bars_ago = $endAgo
            window_from = $res.window_from; window_to = $res.window_to
            trade_count = $res.trade_count; win_rate = $res.win_rate
            expectancy_r = $res.expectancy_r; total_r = $res.total_r
            profit_factor = $res.profit_factor; max_drawdown_pct = $res.max_drawdown_pct
            trades_csv = $res.trades_csv
        }
        $n++
        Write-Host ("  trades={0} expR={1}" -f $res.trade_count, $res.expectancy_r)
    }
}
$sweep | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir 'sweep-summary-v3.json')
$csvLines = @('style,maxBars,end_bars_ago,window_from,window_to,trade_count,win_rate,expectancy_r,total_r,profit_factor,max_drawdown_pct')
foreach ($s in $sweep) {
    $csvLines += ('{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10}' -f `
        $s.style, $s.maxBars, $s.end_bars_ago, $s.window_from, $s.window_to, `
        $s.trade_count, $s.win_rate, $s.expectancy_r, $s.total_r, $s.profit_factor, $s.max_drawdown_pct)
}
$csvLines | Set-Content (Join-Path $outDir 'sweep-summary-v3.csv')
$withTrades = @($sweep | Where-Object { $_.trade_count -gt 0 })
Write-Host ("rolling done n={0} with_trades={1}" -f $sweep.Count, $withTrades.Count)
Write-Host 'DONE'
