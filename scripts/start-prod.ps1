#Requires -Version 5.1
<#
.SYNOPSIS
  Template for G:\apps\trading-portal\start.ps1 (PROD).
  Keep deployed copy in sync — clears inherited SPRING_DATASOURCE_* and pins schema=prod.
#>
param()
$ErrorActionPreference = 'Stop'
$Root = if ($PSScriptRoot -match 'scripts$') { 'G:\apps\trading-portal' } else { $PSScriptRoot }
$Secrets = 'E:\MyAgent\workflow\db\secrets\postgres.env'
Get-Content $Secrets | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $p = $_ -split '=', 2
  Set-Item -Path ("Env:" + $p[0].Trim()) -Value $p[1].Trim().Trim('"')
}
@('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD') | ForEach-Object {
  Remove-Item -Path ("Env:" + $_) -ErrorAction SilentlyContinue
}
$profile = 'prod'
$apiPort = 5340
$uiPort = 5341
$jar = Join-Path $Root 'app\api\trading-portal-backend-0.3.2.jar'
$ui = Join-Path $Root 'app\ui'
if (-not (Test-Path $jar)) { throw "missing jar $jar" }
foreach ($p in @($apiPort, $uiPort)) {
  $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($c) {
    $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
    if ($proc -and ($proc.ProcessName -match 'java|python|node')) {
      Write-Host "Stopping $($proc.ProcessName) pid $($proc.Id) on :$p"
      Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
  }
}
Start-Sleep -Seconds 1
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'logs') | Out-Null
$apiOut = Join-Path $Root ("logs\api-$profile.out.log")
$apiErr = Join-Path $Root ("logs\api-$profile.err.log")
$uiOut = Join-Path $Root ("logs\ui-$profile.out.log")
$uiErr = Join-Path $Root ("logs\ui-$profile.err.log")
Write-Host "Starting API profile=$profile port=$apiPort"
$extra = @(
  '--spring.datasource.hikari.maximum-pool-size=5',
  '--spring.datasource.hikari.minimum-idle=1',
  '--spring.datasource.hikari.idle-timeout=30000',
  '--spring.datasource.hikari.keepalive-time=30000',
  '--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/app_trading_portal?currentSchema=prod',
  '--spring.datasource.username=app_trading_portal_prod',
  "--spring.datasource.password=$($env:TRADING_PORTAL_ROLE_PROD_PASSWORD)",
  '--spring.jpa.properties.hibernate.default_schema=prod'
)
Start-Process -FilePath 'java' -ArgumentList (@('-jar', $jar, "--spring.profiles.active=$profile") + $extra) -WorkingDirectory (Join-Path $Root 'app\api') -RedirectStandardOutput $apiOut -RedirectStandardError $apiErr -WindowStyle Hidden
Write-Host "Starting UI python http.server port=$uiPort"
Start-Process -FilePath 'python' -ArgumentList @('-m','http.server', "$uiPort", '--bind', '127.0.0.1') -WorkingDirectory $ui -RedirectStandardOutput $uiOut -RedirectStandardError $uiErr -WindowStyle Hidden
Write-Host "Launched. Smoke: http://127.0.0.1:$apiPort/api/health and http://127.0.0.1:$uiPort/"
