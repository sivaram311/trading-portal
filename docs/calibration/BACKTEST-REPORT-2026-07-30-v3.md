# XAUUSD ICT + Gann Paper Backtest Report v3

**Date:** 2026-07-30  
**Vs:** [`BACKTEST-REPORT-2026-07-30-v2.md`](./BACKTEST-REPORT-2026-07-30-v2.md)  
**Scope:** Paper-only · `live_enabled=false` · no broker orders  
**Trigger:** User `proceed` → fix `GANN_DATA_GAP` + Mode T watch-only + rolling windows  

---

## 1. Executive summary

| Item | v2 | v3 (this report) |
|------|----|------------------|
| `GANN_DATA_GAP` in census | **66/99** | **0/88** (+0/12 late refill) |
| Mode T paper-confirm | Allowed (bug vs theory) | **Blocked** (watch-only) |
| Rolling 1000-bar × step 250 | Not run | **51 runs · 3 with trades** |
| Trades | 3 Mode **T** fills (1 signal) | 3 Mode **C** fills (1 signal, all losers) |
| Edge claimable? | No | **Still no** |

**Verdict:** Spurious Gann gaps cleared. Mode T no longer paper-opens (Jul 22 v2 signal correctly disappears). One path-dependent Mode **C** / grade **A** long (~2026-06-15) appears in a single rolling window and exits **BE_STOP** negative R across styles. **No edge.**

---

## 2. Code fixes shipped

1. **`GannEngine.preferEntryBars(m5, m15)`** — use M5 only when ≥3 bars; else M15.  
2. **`PipelineService`** — was always passing M5 into Gann (empty before ~2026-07-06 → `DATA_GAP`). Now uses prefer helper.  
3. **`Backtester`** — same prefer helper (also avoids thin M5 windows).  
4. **Mode T policy** — see [`MODE-T-POLICY.md`](./MODE-T-POLICY.md): automation deny + `PaperDecisionPolicy` rejects `T`.  
5. **Backtest API** — `endBarsAgo` rolling param; paper `maxBars` hard cap **5000**.

---

## 3. Confluence census

| Metric | v3 |
|--------|-----|
| Samples | 88 (token expired mid-run) + 12 late refill |
| `GANN_DATA_GAP` | **0** |
| Grades (88) | F 13 · B 51 · C 22 · **A 2** · A+ 0 |
| Modes (88) | NONE 62 · T 25 · **C 1** · R 0 |
| Automation | **deny 88/88** |
| Spot A rows | Mode **T** + SOFT → correctly **deny** |

Artifacts: `results/confluence-sample-summary-v3.json`, `confluence-sample-rows-v3.json`, `confluence-sample-late-v3.json`, `spot-check-v3.json`

---

## 4. Rolling-window sweep

- Window **1000** M15 · step **250** · styles SCALP/DAY/POSITIONAL · `endBarsAgo` 0…4000  
- **51/51** completed · **48 zero-trade** · **3** with trades (same window)

| Style | endBarsAgo | Window | trades | expectancy_r | Signal |
|-------|------------|--------|--------|--------------|--------|
| SCALP | 2250 | 2026-06-10 → 06-25 | 1 | **−0.14** | long · **C** · A · BE_STOP |
| DAY | 2250 | same | 1 | **−0.12** | long · **C** · A · BE_STOP |
| POSITIONAL | 2250 | same | 1 | **−0.11** | long · **C** · A · BE_STOP |

### Trade detail

| Style | Entry | Exit | Side | Mode/Grade | R | Reason |
|-------|-------|------|------|------------|---|--------|
| SCALP | 2026-06-15T18:15Z @ 4359.16 | 18:45Z @ 4383.87 | long | C / A | −0.14 | BE_STOP |
| DAY | 2026-06-15T17:30Z @ 4365.70 | 18:00Z @ 4390.74 | long | C / A | −0.12 | BE_STOP |
| POSITIONAL | 2026-06-15T17:30Z @ 4365.70 | 18:00Z @ 4390.74 | long | C / A | −0.11 | BE_STOP |

Adjacent windows (2000 / 2500 endBarsAgo) → **0 trades** → still path-dependent.

CSV: [`results/sweep-summary-v3.csv`](./results/sweep-summary-v3.csv)

### vs v2 Jul 22 Mode T

Window covering Jul 22 (`endBarsAgo=500`) → **0 trades** after Mode T gate — expected.

---

## 5. Takeaways

1. **`GANN_DATA_GAP` was a pipeline TF bug**, not null `ny_time` in Postgres.  
2. **Mode T must stay watch-only**; v2 “edge” was invalid under theory.  
3. First real Mode **C** paper fills exist but are **tiny-n, window-fragile, negative R**.  
4. Strategy remains extremely selective; promote / P5 still **HOLD**.

---

## 6. Next (paper only)

1. Inspect Jun 15 Mode C path (why BE_STOP negative with higher exit print).  
2. Longer soak / denser replay around ALIGN_R setups (still 0 Mode R in census).  
3. Keep MT5 ingest fresh; optional full 5k single-run once cost allows.  
4. **Not without unlock phrase:** P5 micro-live.

---

**Bottom line:** Engineering quality improved (Gann + Mode T). Measured paper edge remains **absent**.
