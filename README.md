# Trading Portal

Automated trading **application + portal** for **XAUUSD (gold)**.

**Status:** Live on F/G at **`v0.3.2`** (paper + P5 fail-closed) · DEV on `E:\MyWorkspace`  
**Created:** 2026-07-15  
**Feature honesty:** `docs/FEATURE-VALIDATION-0.3.1.md` · `GET /api/ops/status` → `features`

## Scope (v0 / v1)

| Pillar | Focus |
|--------|--------|
| **ICT** | Inner Circle Trader concepts applied to gold — liquidity, structure, sessions, FOLB/FVG, killzones |
| **W.D. Gann** | Intraday angles, Square of 9, time cycles / squaring, session pivots |
| **Automation** | Signal engines → risk rules → (later) execution adapters |
| **Portal** | Operator UI for signals, confluence, journal, and controls |

Reference only (do **not** fork as source of truth): `E:\Source\grok_dev` — market grid, Gann Intraday, NY Liquidity Analyzer, MT5 Python pipeline.

## Layout

```
trading-portal/
├── agents/           # Crew + pre-work / hires
├── docs/             # Theory, algorithms, contracts, OPS, calibration
├── backend/          # Spring Boot 3.3 API (:3340 DEV)
├── frontend/         # Angular operator UI (:3341 DEV)
├── python/           # MT5 ingest worker (:3342 DEV)
└── scripts/          # run-api-dev, fleet checks, calibration
```

## Run DEV (MyAgent)

```powershell
# API — loads DB secrets from E:\MyAgent\workflow\db\secrets\postgres.env
powershell -File scripts\run-api-dev.ps1

# UI static for public DEV host (nginx root)
cd frontend; npx ng build --configuration public-dev

# Optional local ng serve
cd frontend; npm start   # http://127.0.0.1:3341
```

**Public DEV:** https://trading-portal-dev.delena.buzz  
- nginx: `deploy/nginx/trading-portal-dev.delena.buzz.conf` → live `C:\nginx-1.30.3\conf\apps\`  
- UI: `frontend/dist/public-dev/browser` · API `:3340` · auth/JWKS **css-next `:4910`**  
- Reload nginx via Session-0 task `NginxReload-ProductionHouse` (not interactive `nginx -s reload`)

Ports: **3340** API · **3341** UI serve · **3342** ingest — reserved in `E:\MyAgent\workflow\ports\`.  
P5 micro-live stays **fail-closed** until exact unlock phrase.

## Next

1. MT5 OHLC backfill (terminal IPC currently timing out) then re-run backtest sweep
2. Paper calibration sweeps (`docs/calibration/`) after non-zero A/A+ sample
3. Promote only after Reviewer GO + user promote decision

**Latest paper backtest:** [`docs/calibration/BACKTEST-REPORT-2026-07-30.md`](docs/calibration/BACKTEST-REPORT-2026-07-30.md)
