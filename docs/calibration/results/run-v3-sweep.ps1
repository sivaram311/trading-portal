# Paper-only v3: GANN_DATA_GAP spot-check + confluence census + rolling 1000-bar windows.
$ErrorActionPreference = 'Stop'
$outDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$token = Get-Content (Join-Path $outDir '.bearer.token') -Raw
$hdr = @{ Authorization = "Bearer $token" }
$api = 'http://127.0.0.1:3340'

function Invoke-Replay([string]$asof) {
    return Invoke-RestMethod -Method POST -Headers $hdr -Uri "$api/api/ops/replay?asof=$asof" -TimeoutSec 90
}

Write-Host '=== spot checks ==='
$early = Invoke-Replay '2026-06-15T14:00:00Z'
$jul22 = Invoke-Replay '2026-07-22T05:15:00Z'
$spot = [ordered]@{
    early = @{
        asof = '2026-06-15T14:00:00Z'
        grade = $early.grade; mode = $early.mode; automation = $early.automation
        gann_data_gap = [bool]($early.reasons -contains 'GANN_DATA_GAP')
        reasons = @($early.reasons)
    }
    jul22 = @{
        asof = '2026-07-22T05:15:00Z'
        grade = $jul22.grade; mode = $jul22.mode; automation = $jul22.automation
        gann_data_gap = [bool]($jul22.reasons -contains 'GANN_DATA_GAP')
        reasons = @($jul22.reasons)
    }
}
$spot | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir 'spot-check-v3.json')
Write-Host ("early gap={0} grade={1} mode={2}" -f $spot.early.gann_data_gap, $spot.early.grade, $spot.early.mode)
Write-Host ("jul22 gap={0} grade={1} mode={2} auto={3}" -f $spot.jul22.gann_data_gap, $spot.jul22.grade, $spot.jul22.mode, $spot.jul22.automation)

Write-Host '=== capabilities ==='
$caps = Invoke-RestMethod -Headers $hdr -Uri "$api/api/backtest/capabilities" -TimeoutSec 30
$caps | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outDir 'capabilities-v3.json')
$m15 = [int]$caps.m15_bars_available
Write-Host "m15_bars=$m15"

Write-Host '=== confluence census (99 steps) ==='
# Use M15 timestamps via capabilities window: evenly sample asofs from OHLC via replay of synthetic offsets.
# Fetch last bar times by walking backtest windows' window_to / use fixed step from known store.
# Simpler: query DB timestamps via rolling endBarsAgo not available for replay — use evenly spaced ISO from May14-Jul30.
$from = [datetime]::Parse('2026-05-14T22:30:00Z').ToUniversalTime()
$to = [datetime]::Parse('2026-07-30T18:15:00Z').ToUniversalTime()
$n = 99
$rows = @()
$grades = @{}
$modes = @{}
$autos = @{}
$dirs = @{}
$reasonCounts = @{}
$gapCount = 0
$aCount = 0
for ($i = 0; $i -lt $n; $i++) {
    $t = $from.AddTicks([int64](($to.Ticks - $from.Ticks) * $i / [Math]::Max(1, $n - 1)))
    $asof = $t.ToString('yyyy-MM-ddTHH:mm:ssZ')
    try {
        $d = Invoke-Replay $asof
    } catch {
        Write-Warning "replay fail $asof : $($_.Exception.Message)"
        continue
    }
    $g = [string]$d.grade; $m = [string]$d.mode; $a = [string]$d.automation; $dir = [string]$d.direction
    if (-not $grades.ContainsKey($g)) { $grades[$g] = 0 }; $grades[$g]++
    if (-not $modes.ContainsKey($m)) { $modes[$m] = 0 }; $modes[$m]++
    if (-not $autos.ContainsKey($a)) { $autos[$a] = 0 }; $autos[$a]++
    if (-not $dirs.ContainsKey($dir)) { $dirs[$dir] = 0 }; $dirs[$dir]++
    $gap = [bool]($d.reasons -contains 'GANN_DATA_GAP')
    if ($gap) { $gapCount++ }
    if ($g -in @('A', 'A+')) { $aCount++ }
    foreach ($rr in @($d.reasons)) {
        if (-not $reasonCounts.ContainsKey($rr)) { $reasonCounts[$rr] = 0 }
        $reasonCounts[$rr]++
    }
    $rows += [ordered]@{
        asof = $asof; grade = $g; mode = $m; automation = $a; direction = $dir
        gann_data_gap = $gap; reasons = @($d.reasons)
    }
    if (($i + 1) % 10 -eq 0) { Write-Host "census $($i+1)/$n" }
}
$topReasons = $reasonCounts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 20 | ForEach-Object { ,@($_.Key, $_.Value) }
$census = [ordered]@{
    m15_bars = $m15
    samples = $rows.Count
    grades = $grades
    modes = $modes
    automations = $autos
    directions = $dirs
    gann_data_gap_count = $gapCount
    confirmable_proxy_A_or_Aplus = $aCount
    top_reasons = $topReasons
}
$census | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir 'confluence-sample-summary-v3.json')
$rows | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir 'confluence-sample-rows-v3.json')
Write-Host ("census gap={0}/{1} A/A+={2}" -f $gapCount, $rows.Count, $aCount)

Write-Host '=== rolling windows maxBars=1000 step=250 ==='
$window = 1000
$step = 250
$maxEndAgo = [Math]::Max(0, $m15 - $window)
$styles = @('SCALP', 'DAY', 'POSITIONAL')
$sweep = @()
for ($endAgo = 0; $endAgo -le $maxEndAgo; $endAgo += $step) {
    foreach ($style in $styles) {
        $uri = "$api/api/backtest/run?maxBars=$window&endBarsAgo=$endAgo&style=$style"
        Write-Host "run style=$style endBarsAgo=$endAgo"
        try {
            $res = Invoke-RestMethod -Method POST -Headers $hdr -Uri $uri -TimeoutSec 300
        } catch {
            Write-Warning "backtest fail $style end=$endAgo : $($_.Exception.Message)"
            continue
        }
        $sweep += [ordered]@{
            style = $style
            maxBars = $window
            end_bars_ago = $endAgo
            window_from = $res.window_from
            window_to = $res.window_to
            trade_count = $res.trade_count
            win_rate = $res.win_rate
            expectancy_r = $res.expectancy_r
            total_r = $res.total_r
            profit_factor = $res.profit_factor
            max_drawdown_pct = $res.max_drawdown_pct
            trades_csv = $res.trades_csv
        }
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
Write-Host ("rolling done windows={0} with_trades={1}" -f $sweep.Count, $withTrades.Count)
Write-Host 'DONE'
