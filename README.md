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

# UI (separate terminal)
cd frontend; npm start   # http://127.0.0.1:3341
```

Ports: **3340** API · **3341** UI · **3342** ingest — reserved in `E:\MyAgent\workflow\ports\`.  
Auth: CSS (`centralized-security-system`); DEV may use `X-Dev-Token: dev-operator-token` when bypass is on.  
P5 micro-live stays **fail-closed** until exact unlock phrase.

## Next

1. Paper calibration sweeps (`docs/calibration/`)
2. DEV smoke of new surfaces (style / analytics / mitigation / multi-day)
3. Promote only after Reviewer GO + user promote decision
