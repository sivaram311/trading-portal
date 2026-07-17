# Feature validation — trading-portal deep-algo / P5

**When:** 2026-07-17  
**Against:** `docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md` phases A–D  
**Live inventory API:** `GET /api/ops/status` → `features` + `trading_style`

## Verdict

**Backend engines for Phase A–C and most of D are implemented.** Not everything is user-visible, and several doc items remain **MISSING** or **PARTIAL**. Seeing only grade/direction/journal does not mean those engines are absent — they were under-exposed until this pass.

| Feature | Status | Where |
|---------|--------|-------|
| OTE + selectEntry | IMPLEMENTED | `OteCalculator`, `IctEngine` |
| EQH/EQL + rounds | IMPLEMENTED | `LiquidityPools` |
| Style SCALP/DAY/POSITIONAL | IMPLEMENTED (backend) | `StyleRegistry` · UI shows active style (no picker) |
| MarketQualityGate | IMPLEMENTED | Pipeline |
| PositionManager BE/T1/trail | IMPLEMENTED | no pyramiding (by design) |
| Journal MFE/MAE/exit | IMPLEMENTED | backend + journal UI row |
| Backtester + WF/MC | IMPLEMENTED | `POST /api/backtest/run` |
| Breaker / IFVG / Unicorn | IMPLEMENTED | `IctEngine` + price-rail overlays |
| DXY SMT | IMPLEMENTED | `SmtDetector` |
| UI overlays OB/FVG/OTE/So9/1×1 | IMPLEMENTED | price rail (not candle chart) |
| P5 micro-live | CODED fail-closed | `live-enabled=false` on F/G |
| Mitigation Block | **MISSING** | — |
| Multi-day Gann cycles | **MISSING** | — |
| Pyramiding | **MISSING** (paper max 1) | — |
| Style selector UI | **MISSING** | config only |
| Analytics dashboard | **PARTIAL** | CSV via backtest API |

## Flyway

- `V1__init.sql` — core tables  
- `V2__live_journal_indexes.sql` — LIVE_* journal indexes (reuse `paper_journal`)

## Still not “all features”

Doc Phase D / calibration / full chart UI / pyramiding / mitigation / multi-day Gann are **not** complete. Treat this file + `/api/ops/status.features` as the honesty bar.
