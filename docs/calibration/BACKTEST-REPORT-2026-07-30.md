# XAUUSD ICT + Gann Paper Backtest Report

**Date:** 2026-07-30 (IST)  
**Environment:** DEV `http://127.0.0.1:3340` · public https://trading-portal-dev.delena.buzz  
**Scope:** Paper-only · `live_enabled=false` · no broker `order_send`  
**Engines:** ICT + W.D. Gann + Confluence + RiskGate + PositionManager  
**Advisor:** Grok 4.5 via OpenRouter (`x-ai/grok-4.5`)  
**Artifacts:** `docs/calibration/results/`

---

## 1. Executive summary

| Question | Answer |
|----------|--------|
| Did the working ICT+Gann stack produce paper trades on stored XAUUSD? | **No — 0 trades across 12 backtest runs** |
| Is that a bug in the backtester? | **No — gates worked as designed** |
| What blocked entries? | **Zero A/A+ grades; automation=`deny` on 100% of sampled as-ofs** |
| Data window | M15 **585 bars** · **2026-07-09 → 2026-07-17** (stale vs “today”) |
| Verdict | **Data-limited + threshold-strict.** Strategy logic is fail-closed; this ~1 week slice never reached paper grade floor. |

**Bottom line:** On the OHLC currently in DEV, the confluence story is mostly *bearish Gann stretch + ICT discount + soft/conflict noise* that tops out at grade **B/C/F**. RiskGate requires **A/A+** for paper → empty trade blotter is the honest result.

---

## 2. High-level thesis (Grok 4.5)

Source: [`GROK-4.5-BACKTEST-PLAN.md`](./results/GROK-4.5-BACKTEST-PLAN.md)

- **Core setup:** ICT OTE/OB/FVG (+ breaker/IFVG/mitigation, EQH/EQL, killzones) stacked with Gann 1×1 / So9 / time-square; multi-day Gann remains observe-only.
- **Tradable only when:** grade **A+/A**, Mode **R** (primary) or resolved **C/T**, killzone alignment, RiskGate ok, max 1 open.
- **Position rules:** BE@1R, T1 partial, ATR trail, limited ADD_LEG.
- **Promotion bar (Grok):** expectancy ≳ 0.4R and WF OOS PF ≳ 1.2 before considering grade-gate changes — **not met** (no trades).

---

## 3. Data inventory

| TF | Bars (ops/status) | Latest bar | Stale? |
|----|-------------------|------------|--------|
| M1 | 1785 | 2026-07-17T18:28Z | yes |
| M5 | 1369 | 2026-07-17T18:20Z | yes |
| **M15** | **585** | **2026-07-17T18:00Z** | **yes** |
| H1 | 521 | (same era) | yes |

- Backtest API cap: `maxBars ≤ 2000`, need ≥80 M15.
- Available M15 ≈ **6–7 trading sessions** — far short of a multi-regime gold study.
- MT5 credentials exist on machine; terminal reachability for *fresh* backfill was not completed in this pass (ingest check left for follow-up).

Capabilities snapshot: [`results/capabilities.json`](./results/capabilities.json)

---

## 4. Sweep matrix executed

Parallel runs via `POST /api/backtest/run` (Bearer CSS JWT):

| Style | maxBars | WF | MC | bars_processed | trade_count | expectancy_r | PF | maxDD% |
|-------|---------|----|----|----------------|-------------|--------------|----|--------|
| SCALP | 200 | — | — | 149 | **0** | 0 | 0 | 0 |
| SCALP | 400 | — | — | 349 | **0** | 0 | 0 | 0 |
| SCALP | 585 | — | — | 534 | **0** | 0 | 0 | 0 |
| SCALP | 585 | ✓ | ✓ | 534 | **0** | 0 | 0 | 0 |
| DAY | 200 | — | — | 149 | **0** | 0 | 0 | 0 |
| DAY | 400 | — | — | 349 | **0** | 0 | 0 | 0 |
| DAY | 585 | — | — | 534 | **0** | 0 | 0 | 0 |
| DAY | 585 | ✓ | ✓ | 534 | **0** | 0 | 0 | 0 |
| POSITIONAL | 200 | — | — | 149 | **0** | 0 | 0 | 0 |
| POSITIONAL | 400 | — | — | 349 | **0** | 0 | 0 | 0 |
| POSITIONAL | 585 | — | — | 534 | **0** | 0 | 0 | 0 |
| POSITIONAL | 585 | ✓ | ✓ | 534 | **0** | 0 | 0 | 0 |

Raw: [`results/sweep-summary.csv`](./results/sweep-summary.csv) · per-run JSON under `results/run-*.json`

Walk-forward / Monte-Carlo add nothing when the base run has **no closed trades** (empty distributions).

---

## 5. Mid-level: confluence behavior (deep sample)

**Method:** 77 evenly spaced M15 as-ofs (after lookback), each `POST /api/ops/replay?asof=…`.

| Dimension | Distribution |
|-----------|----------------|
| Grades | **B 45 · C 17 · F 15 · A/A+ 0** |
| Modes | **NONE 47 · T 30 · R/C 0** |
| Automation | **deny 77 / 77** |
| Direction | short 62 · flat 15 |
| Proxy confirmable (A/A+ ∧ mode≠NONE ∧ auto≠deny) | **0** |

### Top reason codes (hit count / 77)

| Reason | Hits | Read |
|--------|------|------|
| ICT_PD_DISCOUNT | 77 | Always in discount vs dealing range |
| GANN_PIVOT_NY_OPEN | 74 | Session pivot anchored |
| GANN_ANG_OVER_DOWN | 72 | 1×1 stretch oversold/down |
| GANN_ANG_ALERT | 72 | Stretch alert firing often |
| SOFT | 62 | Soft confluence only |
| ICT_DISP_OK | 60 | Displacement present |
| GANN_REV_CANDLE | 32 | Reversal candle filter |
| ICT_SWEEP_* (EQH/ALH/AHH/PDL) | 12–21 | Liquidity sweeps mixed |
| CONFLICT | 12 | Hard ICT↔Gann disagreement |

Artifacts: [`confluence-sample-summary.json`](./results/confluence-sample-summary.json), [`confluence-sample-rows.json`](./results/confluence-sample-rows.json)

---

## 6. Deep dive: why the “working strategy” did not trade

### Gate stack (paper)

1. **ConfluenceEngine** must emit actionable mode (not `NONE`) and not `automation=deny`.
2. **RiskGate** requires grade **A or A+**, no news veto, no midday new-entry, sizeable stop, max 1 open.
3. **MarketQualityGate** can still deny (spread / gap / duplicate).
4. **FillSimulator** needs next bars to trade into the entry zone (SCALP also wants directionally confirming close).

### What actually happened

- Step 1 failed universally: **deny + no R/C modes** in the sample.
- Step 2 would also fail: **no A/A+** even if deny were ignored.
- Fill model never reached — no pending orders.

Latest live decision at report time matched the pattern: `grade=F`, `mode=NONE`, `automation=deny`, reasons include `CONFLICT` + Gann over-down alert.

### ICT vs Gann narrative (this week)

- **ICT:** Persistent discount + displacement + intermittent sweeps — structure “interesting” but not graded to A.
- **Gann:** Persistent angle-over-down alerts from NY open pivot — aggressive downside lean, often only **SOFT** or **CONFLICT** with ICT.
- **Joint:** Bearish-leaning noise, not a clean Mode R A+ killzone package.

Grok interpretation: [`GROK-4.5-ZERO-TRADE-INTERPRETATION.md`](./results/GROK-4.5-ZERO-TRADE-INTERPRETATION.md)

---

## 7. Invalidation / honesty

Per Grok plan invalidation criteria, we **cannot** claim edge:

- No OOS expectancy / PF (no trades).
- No slippage sensitivity study (vacuous).
- No MC 5th-percentile equity (empty).
- **Do not** treat `direction=short` majority as a trade signal.

This is a **successful negative result**: paper filters prevented low-quality entries.

---

## 8. Recommended next experiments (paper only, ranked)

1. **Refresh / extend OHLC** via MT5 ingest (weeks–months of M15/H1/D1), re-run the same 12-cell sweep.
2. **Deny autopsy** — classify why `mode=T` still gets `automation=deny` (rule IDs).
3. **Grade histogram study** on expanded data before touching RiskGate floor.
4. **Ablation (research):** ICT-only vs Gann-only vs joint grade distributions (still no live).
5. Only after ≥30 A/A+ paper candidates in sample: enable meaningful WF/MC and slippage 10/20/30 pts.
6. Calibration checklist items in [`OPEN-CALIBRATION.md`](./OPEN-CALIBRATION.md) remain open (time_scale, So9, weights, etc.) — **do not change defaults** until (1)–(5) produce non-zero A/A+.

### Explicitly out of scope until GO

- P5 micro-live (`GO micro-live P5 on DEV only`)
- Lowering A/A+ floor on any path that can place orders
- Multi-symbol expansion

---

## 9. Method appendix

| Step | How |
|------|-----|
| Auth | css-next `:4910` login, client `trading-portal` |
| Caps | `GET /api/backtest/capabilities` |
| Sweep | Parallel Python `ThreadPoolExecutor` → `POST /api/backtest/run` |
| Confluence census | `POST /api/ops/replay?asof=` on 77 M15 timestamps |
| Advisor | OpenRouter `x-ai/grok-4.5` plan + zero-trade interpretation |
| Parallel agents | Grok plan, auth/caps, zero-trade diagnosis, sweep runner |

Temp runner scripts were local-only and not required for reproduction; use `scripts/calibration/run-weight-sweep.ps1 -BearerToken <jwt>` for future sweeps.

---

## 10. One-page conclusion

The ICT+Gann portal engines **ran**, **graded**, and **refused** to paper-trade XAUUSD on the stored Jul 9–17 2026 M15 window. Comprehensive sweep + replay census show **no A/A+ confirmable setups**. Publish this as the baseline: **edge not measurable until longer, fresher gold history is ingested and the grade distribution is re-measured under the same fail-closed gates.**
