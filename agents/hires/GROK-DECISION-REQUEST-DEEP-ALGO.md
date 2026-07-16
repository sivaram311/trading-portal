# DECISION REQUEST — Deep Algorithms Wave (0.3 engine depth)

**When:** 2026-07-17  
**Session:** `trading-portal-deep-algo-2026-07-17`  
**Requester:** Crew Lead (Cursor Composer)  
**Your role:** Plan reviewer / decision maker for this wave. User said **GO** to implement deep algorithms in one shot after your review.

---

## Machine constraints (non-negotiable)

From `E:\MyAgent\workflow\CONSCIOUS.md`:

- Ports reserved before bind (DEV 3xxx / PREPROD 4xxx / PROD 5xxx).
- Postgres schema-per-env on shared `:5432` (`app_trading_portal`).
- Auth: CSS only — no new IdP.
- No deletes without user confirm.
- No live broker orders / `order_send` / `trading.exec.live-enabled=true`.
- P5 micro-live remains **HOLD** unless user explicitly unlocks.
- Paper-only execution path for this wave.

---

## Context conflict (resolve explicitly)

`ROADMAP-0.3-EXECUTION.md` Track 5 said **engine depth defer** until soak gaps demand it.  
Tracks 1–4 are **done**; `soak_met=true`.  
**User now orders:** implement deep-algo stack in one shot (OTE, EQH/EQL, StyleProfile, PositionManager, MarketQualityGate, Backtester, journal enhancements).

Lead proposes: treat this as unlocking Track 5 under version **0.3.0-SNAPSHOT** (DEV first), still paper-only.

---

## Spec SoT (must read)

1. `docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md` — master formulas + roadmap  
2. `docs/algorithms/ICT-SIGNAL-ENGINE.md`  
3. `docs/algorithms/GANN-CYCLE-ENGINE.md`  
4. `docs/theory/CONFLUENCE-FRAMEWORK.md`  
5. `docs/algorithms/AUTOMATION-PIPELINE.md`

## Living code (extend; do not rewrite)

| Module | Path | Status |
|--------|------|--------|
| ICT | `backend/.../engine/ict/IctEngine.java` | v1 live |
| Gann | `backend/.../engine/gann/GannEngine.java` | v1 live |
| Confluence | `backend/.../engine/confluence/ConfluenceEngine.java` | v1 live |
| Risk | `backend/.../engine/risk/RiskGate.java` | MVP (max 1 pos) |
| Paper | `backend/.../paper/PaperTradingService.java` | confirm/auto A+/dismiss only |
| Pipeline | `backend/.../pipeline/PipelineService.java` | live |

Missing today: OTE, EQH/EQL+rounds, StyleProfile, PositionManager, MarketQualityGate, Backtester, advanced journal (MFE/MAE).

---

## Lead proposed hire plan (parallel, file-partitioned)

### Wave A — parallel (no shared writes if partitions respected)

| Hire | Owner skill | Files owned (exclusive write) | Deliverable |
|------|-------------|-------------------------------|-------------|
| **H1 OTE** | Java/engines | `OteCalculator.java` (new), `IctEngine.selectEntry` + tests, `IctSnapshot` OTE fields if needed | OTE zone + entry prefer OTE∩OB/FVG |
| **H2 Liquidity** | Java/engines | `IctEngine.buildPools` / equal-level helpers + tests | EQH/EQL + ROUND_5/10/50/100 pools |
| **H3 Style** | Java/config | `TradingStyle.java`, `StyleProfile.java`, `StyleRegistry.java`, wire `PipelineService` + configs | SCALP/DAY/POSITIONAL profiles |
| **H4 QualityGate** | Java/risk | `MarketQualityGate.java` + RiskGate/Pipeline hook | spread/gap/regime/duplicate veto |
| **H5 PositionMgr** | Java/paper | `PositionManager.java`, extend paper journal states | BE@1R, partial T1, trail, limited add |
| **H6 Backtester** | Java/backtest | `backtest/*` package (new) | bar-by-bar + metrics export (PF, expectancy R, maxDD) |

### Wave B — after A merges (sequential)

| Hire | Work |
|------|------|
| **H7 Integrate** | Wire Pipeline: style → engines → confluence → quality → risk → position mgr; unit+integration tests green |
| **H8 Docs/UI** | Update algorithm docs status; optional Angular level annotations (OB/FVG/So9/OTE) — only if A+B engines green |

### Coordination rules

1. **No rewrite** of engine cores — extend methods / add classes.  
2. **No live execution** code.  
3. **RiskGate** max-open may become style-aware (legs) only via PositionManager + RiskConfig — H5 owns that change; H4 does not.  
4. Shared models (`IctSnapshot`, `ConfluenceDecision`) — H1/H2 may add optional fields; H7 reconciles compile breaks.  
5. Tests required per hire; `mvn test` must pass after integrate.  
6. DEV only; no F:/G: promote this wave unless you say GOOD TO PROMOTE (unlikely mid-wave).

---

## Questions for you (answer all)

1. **VERDICT:** `PROCEED` | `REVISE` | `HOLD` — unlock Track 5 engine depth now?  
2. **Hire order:** Approve Wave A parallel as above, or serialize some hires? Which first if serialize?  
3. **Scope cut:** Anything MUST defer (e.g. pyramiding, Breaker/IFVG, DXY SMT, Angular overlays)?  
4. **RiskGate:** Allow maxLegs > 1 on paper for DAY/POSITIONAL this wave, or keep max 1 until backtester proves?  
5. **Backtester:** Must H6 ship in this wave, or after PositionManager only?  
6. **MUST_NOT** list for this wave (add any).  
7. **Success criteria** before Lead may ask you again (tests? sample backtest run? paper smoke?).

---

## Required output format (plain text)

```
VERDICT=PROCEED|REVISE|HOLD
REASON=...
UNLOCK_TRACK5=yes|no
WAVE_A_ORDER=H1,H2,...   # parallel ok if comma list; use > for sequence
DEFER=...
MAX_LEGS_PAPER=1|style
BACKTESTER=this_wave|after_pm|defer
MUST_NOT=...
SUCCESS_BEFORE_REASK=...
FOLLOW_FIRST=...
```

Do **not** write application code. Review the plan only.
