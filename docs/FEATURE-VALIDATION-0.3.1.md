# Feature validation — trading-portal deep-algo / P5

**When:** 2026-07-17 (baseline) · **updated 2026-07-30** (pending-feature wave **pushed** + paper backtest baseline)  
**Live tag:** `v0.3.2` on F/G (paper + P5 fail-closed) · DEV/`main` ahead with post-0.3.2 feature wave  
**Against:** `docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md` phases A–D  
**Live inventory API:** `GET /api/ops/status` → `features` + `trading_style`  
**Backtest baseline:** `docs/calibration/BACKTEST-REPORT-2026-07-30.md`

## Verdict

**Phase A–D pending gaps from the prior honesty pass are implemented in this working tree.** Backend `mvn test` is **green** (124 tests). Frontend production build was green earlier this wave. P5 remains fail-closed.

| Feature | Status | Where |
|---------|--------|-------|
| OTE + selectEntry | IMPLEMENTED | `OteCalculator`, `IctEngine` |
| EQH/EQL + rounds | IMPLEMENTED | `LiquidityPools` |
| Style SCALP/DAY/POSITIONAL | IMPLEMENTED | `StyleRegistry` + `GET`/`PUT /api/style` + UI picker |
| MarketQualityGate | IMPLEMENTED | Pipeline |
| PositionManager BE/T1/trail + ADD_LEG | IMPLEMENTED | `PositionManager` / `PyramidPolicy`; **max 1 open** (legs on same position) |
| Journal MFE/MAE/exit | IMPLEMENTED | backend + journal UI row |
| Backtester + WF/MC | IMPLEMENTED | `POST /api/backtest/run` |
| Breaker / IFVG / Unicorn | IMPLEMENTED | `IctEngine` + overlays |
| DXY SMT | IMPLEMENTED | `SmtDetector` |
| P5 micro-live | CODED fail-closed | `live-enabled=false` — unchanged |
| UI overlays (price rail + candles) | IMPLEMENTED | `CandleChartComponent` + `PriceLevelsComponent` |
| Mitigation Block | IMPLEMENTED | `MitigationDetector` + `IctEngine` + `Zones.mitigationBlocks` |
| Multi-day Gann cycles | IMPLEMENTED | `MultiDayCycleCalculator` + `GannEngine` (observe-only) |
| Pyramiding | IMPLEMENTED_STYLE_MAXLEGS_MAX1OPEN | ADD_LEG per style; never opens a 2nd position |
| Style selector UI | IMPLEMENTED | `tp-style-selector` on confluence |
| Analytics dashboard | IMPLEMENTED_UI | `/api/analytics/*` + `/analytics` page |

## 2026-07-30 closeout

- `cd backend; mvn test` → **green** (fixed `GannEngine`↔`MultiDayCycleCalculator` wire, multi-day label `MULTI_DAY_N`, analytics 4-d.p. expectations, DAY `maxLegs=2` in PositionManagerTest).
- Calibration harness docs: `docs/calibration/OPEN-CALIBRATION.md`, `DEFAULTS-v0.3.md`.
- P5 still requires exact unlock phrase before arming DEV.

## Flyway

- `V1__init.sql` — core tables
- `V2__live_journal_indexes.sql` — LIVE_* journal indexes (reuse `paper_journal`)

## Still deferred / operator note

- Open calibration tasks (ATR time_scale, So9 steps, WF weight sweep) — docs/scripts only; defaults unchanged.
- P5 micro-live arming — HOLD until `GO micro-live P5 on DEV only`.
- **Paper backtest baseline (2026-07-30):** [`docs/calibration/BACKTEST-REPORT-2026-07-30.md`](./calibration/BACKTEST-REPORT-2026-07-30.md) — 12/12 runs **0 trades** on M15 Jul 9–17 window (no A/A+); MT5 backfill blocked (IPC timeout). Treat `/api/ops/status.features` as the honesty bar after deploy.
- FEATURE-VALIDATION header: wave is **pushed** on `main` (DEV); F/G still `v0.3.2`.
