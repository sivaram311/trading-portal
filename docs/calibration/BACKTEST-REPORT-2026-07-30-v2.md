# XAUUSD ICT + Gann Paper Backtest Report v2

**Date:** 2026-07-30  
**Vs:** [`BACKTEST-REPORT-2026-07-30.md`](./BACKTEST-REPORT-2026-07-30.md) (v1 zero-trade baseline)  
**Scope:** Paper-only · `live_enabled=false` · no broker orders  
**Trigger:** User `proceed` → MT5 ingest backfill → re-sweep  

---

## 1. Executive summary

| Item | v1 | v2 (this report) |
|------|----|------------------|
| M15 bars in DEV | 585 (stale, ended 2026-07-17) | **5000** (fresh through **2026-07-30T05:15Z**) |
| MT5 | IPC timeout / unavailable | **Available** — upserted 5k/TF (D1 3175) |
| Backtest trades | **0 / 12 runs** | **3 / 12 runs** (all `maxBars=1000`) |
| A/A+ in sparse census | 0 / 77 | 0 / 99 (sparse grid still misses rares) |
| Edge claimable? | No | **Still no** — n=1 path-dependent fill |

**Verdict:** Data blocker cleared. Strategy remains **extremely selective**. One shared Mode **T** / grade **A** long on **2026-07-22** appears under the 1000-bar window; SCALP exits TIME +0.09R, DAY/POSITIONAL TIME losers. **Not evidence of edge.**

---

## 2. What we did

1. Confirmed `terminal64` in **same session** as ingest worker (Session 2).
2. `run-ingest-dev.ps1 -Mode mt5 --bars 5000` → sync complete.
3. Parallel paper sweep: styles × `{500,1000,2000}` + WF/MC on 2000 (API hard cap).
4. Confluence census: 99 replay as-ofs across **2026-05-14 → 2026-07-30**.

---

## 3. Data inventory (after backfill)

| TF | Count | Latest | Stale |
|----|-------|--------|-------|
| M1 | 6785 | 2026-07-30T05:38Z | false |
| M5 | 5000 | 2026-07-30T05:30Z | false |
| **M15** | **5000** | **2026-07-30T05:15Z** | **false** |
| H1 | 5000 | 2026-07-30T04:00Z | false |
| H4 | 5000 | 2026-07-30T00:00Z | true* |
| D1 | 3175 | 2026-07-29 | true* |

\*ops stale thresholds; still usable for HTF context.

Artifacts: `results/capabilities-v2.json`

---

## 4. Sweep results

| Style | maxBars | trades | win_rate | expectancy_r | total_r | Notes |
|-------|---------|--------|----------|--------------|---------|-------|
| SCALP | 500 | 0 | — | 0 | 0 | |
| SCALP | **1000** | **1** | 1.0 | **+0.09** | +0.09 | PF cosmetic (single win) |
| SCALP | 2000 | 0 | — | 0 | 0 | |
| SCALP | 2000 WF+MC | 0 | — | 0 | 0 | |
| DAY | 500 | 0 | — | 0 | 0 | |
| DAY | **1000** | **1** | 0 | **−0.03** | −0.03 | |
| DAY | 2000 | 0 | — | 0 | 0 | |
| DAY | 2000 WF+MC | 0 | — | 0 | 0 | |
| POSITIONAL | 500 | 0 | — | 0 | 0 | |
| POSITIONAL | **1000** | **1** | 0 | **−0.15** | −0.15 | |
| POSITIONAL | 2000 | 0 | — | 0 | 0 | |
| POSITIONAL | 2000 WF+MC | 0 | — | 0 | 0 | |

CSV: [`results/sweep-summary-v2.csv`](./results/sweep-summary-v2.csv)

### The only signal (all three styles)

| Field | Value |
|-------|--------|
| Entry | **2026-07-22T05:15:00Z** |
| Side / mode / grade | **long · T · A** |
| Entry price | 4122.78 |
| SCALP exit | 2026-07-22T06:15Z · **TIME** · **+0.09R** · 4138.16 |
| DAY exit | 2026-07-22T13:30Z · **TIME** · **−0.03R** · 4117.04 |
| POSITIONAL exit | 2026-07-27T05:30Z · **TIME** · **−0.15R** · 4096.49 |

Interpretation: same confirmable decision; **style `maxHold`** dominates outcome. Mode **T** is time-watch — not the Mode **R** core thesis from Grok’s plan.

### Lookback sensitivity

Same calendar event is **taken** at `maxBars=1000` and **absent** at `maxBars=2000`. Longer HTF/M15 context changes ICT/Gann/confluence enough that the bar is no longer A/confirmable (or never pending-fills). Treat as **fragile / path-dependent**, not robust edge.

---

## 5. Confluence census (sparse)

| Metric | v2 |
|--------|-----|
| Samples | 99 |
| Grades | F 70 · B 20 · C 9 · **A/A+ 0** |
| Modes | NONE 82 · T 17 · R/C 0 |
| Automation | **deny 99/99** |
| Notable | **GANN_DATA_GAP 66/99** · ICT_PD_PREMIUM 99 |

Sparse grid **missed** the Jul 22 A (step≈50 bars). Census is for regime shape, not rare-event detection.

[`confluence-sample-summary-v2.json`](./results/confluence-sample-summary-v2.json)

---

## 6. Deep takeaways

1. **Ingest works** when MT5 runs in the worker’s Windows session with raised init timeout.
2. Paper gates still almost never fire; when they do, it was **Mode T / grade A**, not Mode R confluence.
3. **Hold-time policy** flipped SCALP small win → DAY/POSITIONAL TIME losses on the same entry.
4. **GANN_DATA_GAP** frequency in replay is a quality bug/investigation item (M5 `ny_time` / windowing) — likely suppressing grades on many as-ofs.
5. API **2000-bar cap** means full 5000-bar store isn’t a single-run backtest; need rolling windows or API raise (paper-only change).

---

## 7. Next experiments (paper only, ranked)

1. **Investigate `GANN_DATA_GAP`** on replay as-ofs — fix data/window so Gann doesn’t fail closed spuriously.
2. **Rolling 1000-bar windows** across the 5000 store (step 250) — count A/A+ confirms + fills, not one cherry window.
3. **Mode audit** — should Mode **T** be paper-confirmable? Spec vs `PaperDecisionPolicy` (research decision; don’t loosen live).
4. Raise backtest `maxBars` hard cap or add `from/to` for multi-window studies.
5. Keep MT5 daemon (`--daemon --health` on :3342) so DEV OHLC stays fresh.
6. Only after dozens of closed trades: WF/MC, slippage 10–30 pts, calibration checklist.

### Still HOLD

- P5 unlock (needs exact `GO micro-live P5 on DEV only`)
- F/G promote of post-0.3.2 wave based on this n=1

---

## 8. Conclusion

**v2 unlocked real MT5 history and proved the pipeline can paper-fill** — once. Outcomes are style-hold dominated and **not statistically meaningful**. Next engineering focus: **Gann data-gap**, **rolling-window backtests**, and **Mode T policy clarity** — still paper-only.
