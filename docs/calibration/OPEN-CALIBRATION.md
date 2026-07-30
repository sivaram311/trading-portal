# Open Calibration Tasks (Paper First)

**Status:** Tracking checklist — paper/backtest only.  
**Source of truth:** [`DEEP-ALGORITHMS-AND-CALCULATIONS.md` §14](../algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md#14-open-calibration-tasks-paper-first)  
**Related open items:** [`GANN-CYCLE-ENGINE.md` §11](../algorithms/GANN-CYCLE-ENGINE.md), [`ICT-SIGNAL-ENGINE.md` §9](../algorithms/ICT-SIGNAL-ENGINE.md), [`CONFLUENCE-FRAMEWORK.md` §12](../theory/CONFLUENCE-FRAMEWORK.md)

**Rule:** This file tracks *what to calibrate and how to test it*. It does **not** change any
engine default. Defaults only move after a walk-forward/backtest run shows a clear improvement,
a human signs off, and the change is made deliberately in code (see
[`DEFAULTS-v0.3.md`](./DEFAULTS-v0.3.md) for the current values and where they live).

No live trading is implied or required by anything in this file. `trading.exec.live-enabled`
stays `false` throughout calibration.

**Latest evidence (2026-07-30 v3):** [`BACKTEST-REPORT-2026-07-30-v3.md`](./BACKTEST-REPORT-2026-07-30-v3.md) —
`GANN_DATA_GAP` fixed; Mode T watch-only enforced; rolling windows still show **no claimable edge**
(one path-dependent Mode C loser). Defaults unchanged.

---

## How to use this checklist

1. Pick one open item below.
2. Run `scripts/calibration/run-weight-sweep.ps1` (see
   [usage](../../scripts/calibration/run-weight-sweep.ps1)) against a local DEV backend to collect
   `POST /api/backtest/run` (optionally `-WalkForward`) results across the styles/bar-windows the
   current API supports.
3. Record results (win rate, profit factor, expectancy_r, max_drawdown_pct) per run in a dated
   note under `docs/calibration/results/` (create on first use) or in the PR description.
4. If a change looks justified, open a separate, explicit PR that edits the config
   (`GannConfig`/`IctConfig`/`ConfluenceEngine`/`application*.properties`) with the evidence
   linked. Do not fold calibration data-gathering and default-changing into the same change.
5. Check the box below once the task has a defaults change merged (or a documented decision to
   keep current defaults).

---

## Checklist

- [ ] **`time_scale` by ATR quartile** — `GannConfig.timeScale` (currently a single constant,
  `1.0`, see [`GannConfig.java`](../../backend/src/main/java/com/delena/tradingportal/engine/gann/GannConfig.java))
  is used as `expected_move = time_scale * minutes` for time-squaring (`GANN-CYCLE-ENGINE.md`
  §time-squaring). Bucket historical ATR(14) on M15 into quartiles (Q1..Q4) and backtest whether a
  per-quartile `time_scale` produces more/better `TSQ_*` reactions than the flat `1.0`.
  Blocked on: no per-request override exists yet in `BacktestConfig`/`BacktestController` — a
  quartile sweep today requires either (a) temporary local edits to `GannConfig.defaults()` before
  each backtest run (revert after), or (b) adding a `timeScale` query param to
  `POST /api/backtest/run` in a follow-up PR (not done here, per "docs + harness only").

- [ ] **So9 step sizes that historically produced reactions on XAUUSD** — `GannConfig.so9FineSteps`
  (`[0.25, 0.5, 1.0]`) and `so9OddNMax` (`4`) drive the Square-of-9 level grid
  (`build_so9` in `GANN-CYCLE-ENGINE.md`). Test alternate step sets (e.g. finer `0.125` steps, or
  wider `[0.5, 1.0, 2.0]`) for hit-rate against actual reversals/reactions. Same blocker as above —
  no runtime override; requires editing `GannConfig` locally per sweep run.

- [ ] **Confluence weight optimization (walk-forward)** — the scoring weights are the `+N` literals
  inside `ConfluenceEngine.decide()` (see
  [`ConfluenceEngine.java` lines 98-133](../../backend/src/main/java/com/delena/tradingportal/engine/confluence/ConfluenceEngine.java#L98-L133)):
  sweep (+2), MSS (+2), active entry zone (+1), PD-array alignment (+1), Mode R angle alert (+2),
  Mode C trend (+2), So9 at-level (+1), time-square near (+1), killzone (+1), volume-spike/reversal
  filter (+1), SMT confluence (±1). There is currently **no `WeightSet`/config object** — weights
  are inline. Use `run-weight-sweep.ps1 -WalkForward` to gather baseline walk-forward
  expectancy/profit-factor per style first; a weight-externalization refactor (turning the literals
  into a versioned `ConfluenceWeights` record keyed by `trading.confluence.weights-version`) is a
  prerequisite for an automated sweep and is out of scope for this doc/script drop.

- [ ] **News blackout windows (NFP, FOMC, CPI ±30–60 min)** — `trading.news.blackouts[]`
  (`TradingProperties.News`, see
  [`TradingProperties.java` lines 227-237](../../backend/src/main/java/com/delena/tradingportal/config/TradingProperties.java#L227-L237))
  is empty by default in every profile (`application*.properties` have no `trading.news.blackouts`
  entries — see commented examples in `application.properties` lines 40-46). Populate a real
  calendar (absolute UTC windows or recurring NY-local bands) for NFP/FOMC/CPI ±30–60 min and
  confirm `NewsCalendarService.isVeto()` fires `NEWS_VETO` in `ConfluenceEngine` during those bars
  in a backtest.

- [ ] **Slippage/spread model realistic for gold (10–30 pts typical)** — `BacktestConfig.defaults()`
  currently ships `spreadPts=2.0`, `slippagePts=0.0` (see
  [`BacktestConfig.java` line 24](../../backend/src/main/java/com/delena/tradingportal/backtest/BacktestConfig.java#L24)),
  which is well below the 10–30 pts typical gold slippage/spread named in §14. `FillSimulator`
  (half-spread + slippage, spread-veto above `StyleProfile.maxSpreadPts`) already supports wider
  values — only the constructed default is optimistic. Sweep `spreadPts`/`slippagePts` in the
  10–30 range (per style: SCALP `maxSpreadPts=25`, DAY `32`, POSITIONAL `40` —
  [`StyleRegistry.java`](../../backend/src/main/java/com/delena/tradingportal/engine/style/StyleRegistry.java))
  to see how much edge survives realistic costs. No runtime override exists on
  `POST /api/backtest/run` today (`BacktestConfig` is hardcoded per style in the controller) —
  sweeping requires local `BacktestConfig` edits per run until a follow-up adds query params.

- [ ] **Optimal `equal_eps_pts` by ATR regime** — `IctConfig.equalEpsPts` (`0.6` pts flat, see
  [`IctConfig.java` line 15](../../backend/src/main/java/com/delena/tradingportal/engine/ict/IctConfig.java#L15))
  is the equal-highs/equal-lows tolerance feeding `LiquidityPools` (EQH/EQL). `ICT-SIGNAL-ENGINE.md`
  §9 already flags this as "TBD". Bucket by ATR regime and backtest EQH/EQL sweep-and-reclaim
  hit-rate at tighter (e.g. `0.4`) vs wider (e.g. `1.0`) epsilon.

- [ ] **Whether Mode C requires So9 or only 1×1 hold** — current `ConfluenceEngine` Mode C rule
  (`aligned && ictBos && killzone && gannTrend`, see
  [`ConfluenceEngine.java` line 88](../../backend/src/main/java/com/delena/tradingportal/engine/confluence/ConfluenceEngine.java#L88))
  does **not** require `gann.so9().atLevel()` — it only needs `gannTrend`
  (`gannBias().startsWith("trend_")` or a balanced 1×1 angle bias). This matches "only 1×1 hold"
  already; the open question is whether *adding* a So9-at-level requirement to Mode C improves
  win rate/profit factor enough to justify tightening it. `CONFLUENCE-FRAMEWORK.md` §12 lists this
  as open item #2 ("Whether Mode C should require So9 or only angle hold").

- [ ] **ICT EQ vs Gann 1×1 as T1 when both exist** — `ConfluenceEngine.targets()` currently takes
  `Math.max`/`Math.min` of `ict.htf().eq()` and `gann.angle().equilibrium()` to pick the *nearer*
  level as T1 (see
  [`ConfluenceEngine.java` lines 292-309](../../backend/src/main/java/com/delena/tradingportal/engine/confluence/ConfluenceEngine.java#L292-L309)).
  §14 calls this out as unresolved: test whether always preferring the ICT EQ, always preferring
  the Gann 1×1, or the current nearer-level heuristic yields the best T1 hit-rate/R distribution.

---

## Cross-cutting blocker (read before running sweeps)

`POST /api/backtest/run` (see [`BacktestController.java`](../../backend/src/main/java/com/delena/tradingportal/web/BacktestController.java))
only exposes `maxBars`, `style`, `walkForward`, `monteCarlo`, `mcIterations` as query params today.
It does **not** accept overrides for `time_scale`, `so9FineSteps`, confluence weights,
`spreadPts`/`slippagePts`, or `equal_eps_pts` — those are baked into `GannConfig.defaults()`,
`IctConfig.defaults()`, `BacktestConfig.defaults(style)`, and the `ConfluenceEngine` literals.

`run-weight-sweep.ps1` sweeps what the API supports today (style × walk-forward × bar window) so
you get a real baseline immediately. Any sweep of the engine-level parameters above requires
either:

- Temporary local edits to the relevant config class, rerun, revert (manual, slow, but zero risk
  to shared defaults), or
- A follow-up PR that threads sweep parameters through `BacktestConfig`/`BacktestController` (not
  implemented here — flagged as a prerequisite, not silently added).

This file intentionally does not implement that follow-up so engine defaults cannot drift as a
side effect of "just adding docs."
