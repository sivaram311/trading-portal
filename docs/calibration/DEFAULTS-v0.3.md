# Engine Defaults — v0.3 Snapshot

**Purpose:** Single reference for the *current* values every open calibration task in
[`OPEN-CALIBRATION.md`](./OPEN-CALIBRATION.md) will sweep away from — with exact file/line
pointers so nobody has to re-derive "what is the default today" before a sweep.

**As of:** 2026-07-30. Re-generate this snapshot (or diff it) whenever `GannConfig`, `IctConfig`,
`StyleRegistry`, `BacktestConfig`, `ConfluenceEngine`, or `application*.properties` change.

This is a read-only snapshot. **Do not edit engine code from this document** — see
`OPEN-CALIBRATION.md` for the process to propose a change.

---

## 1. `GannConfig` defaults

Source: [`GannConfig.java`](../../backend/src/main/java/com/delena/tradingportal/engine/gann/GannConfig.java)

| Field | Default | Notes |
|---|---|---|
| `atrPeriod` | `14` | Wilder ATR period, shared convention with ICT. |
| `atrAlert` | `1.25` | Overridden per style (SCALP `1.0`, POSITIONAL `1.5` — see §3). |
| `timeScale` | `1.0` | §14 open task: optimal value by ATR quartile. |
| `nearTimeMin` | `5` | Minutes tolerance for "near" a time-square milestone. |
| `so9NearPct` | `0.0008` (0.08%) | So9 proximity tolerance, percentage term. |
| `so9NearPts` | `0.5` | So9 proximity tolerance, absolute points term. |
| `so9FineSteps` | `[0.25, 0.5, 1.0]` | §14 open task: step sizes to sweep. |
| `so9OddNMax` | `4` | Odd-number Square-of-9 ring cap. |
| `milestonesMin` | `[45, 90, 180]` | Time-squaring checkpoints (minutes). |
| `sessionLenMin` | `540` | Session length (minutes) for cycle fractions. |
| `cycleFractions` | `[0.125, 0.25, 0.333, 0.5, 0.75, 0.875]` | Multi-day cycle checkpoints. |
| `volSpikeMult` | `1.8` | Volume spike filter multiplier. |
| `entryTfMinutes` | `5` | Entry timeframe in minutes. |

## 2. `IctConfig` defaults

Source: [`IctConfig.java`](../../backend/src/main/java/com/delena/tradingportal/engine/ict/IctConfig.java)

| Field | Default | Notes |
|---|---|---|
| `swingNM15` | `2` | Swing lookback (M15). |
| `swingNH1` | `3` | Swing lookback (H1). |
| `equalEpsPts` | `0.6` | §14 open task: optimal value by ATR regime. |
| `sweepReclaimBars` | `3` | Bars allowed for sweep + reclaim confirmation. |
| `minFvgAtrFrac` | `0.5` | Minimum FVG size as ATR fraction. |
| `minFvgPts` | `0.8` | Minimum FVG size in points (DAY style; SCALP/POSITIONAL override — see §3). |
| `displacementBodyFrac` | `0.60` | Minimum candle body fraction to count as displacement. |
| `atrPeriod` | `14` | Shared with Gann. |

## 3. Style overrides (`StyleRegistry`)

Source: [`StyleRegistry.java`](../../backend/src/main/java/com/delena/tradingportal/engine/style/StyleRegistry.java)
— each style starts from `GannConfig.defaults()` / `IctConfig.defaults()` above and overrides only
the fields listed:

| Style | `ict.minFvgPts` | `gann.atrAlert` | `riskPct` | `maxLegs` | `maxHold` | `requireKillzone` | `beTriggerR` | `scaleOutPct` | `maxSpreadPts` |
|---|---|---|---|---|---|---|---|---|---|
| **SCALP** | `0.4` | `1.0` | `0.4` | `1` | `45m` | `true` | `0.75` | `0.50` | `25.0` |
| **DAY** (default, `trading.style`) | `0.8` (base) | `1.25` (base) | `0.625` | `2` | `8h` | `false` | `1.0` | `0.45` | `32.0` |
| **POSITIONAL** | `1.5` | `1.5` | `0.875` | `3` | `5d` | `false` | `1.0` | `0.40` | `40.0` |

`maxSpreadPts` is the ceiling `FillSimulator` uses to veto a fill as `SPREAD_TOO_WIDE` — relevant
context for the slippage/spread calibration task (§14).

## 4. `BacktestConfig` defaults (paper backtest tunables)

Source: [`BacktestConfig.java`](../../backend/src/main/java/com/delena/tradingportal/backtest/BacktestConfig.java)

| Field | `defaults(style)` | `defaultsNextBarOnly(style)` |
|---|---|---|
| `lookbackBars` | `50` | `50` |
| `spreadPts` | `2.0` | `2.0` |
| `slippagePts` | `0.0` | `0.0` |
| `weightsVersion` | `"v1"` | `"v1"` |
| `newsVeto` | `false` | `false` |
| `fillValidityBars` | `4` | `1` |

`spreadPts=2.0` / `slippagePts=0.0` is well below the 10–30 pts realistic gold slippage named in
§14 — see the calibration checklist entry.

`WalkForwardConfig.defaults()` (source:
[`WalkForwardConfig.java`](../../backend/src/main/java/com/delena/tradingportal/backtest/WalkForwardConfig.java)):
`trainBars=200`, `testBars=60`, `stepBars=60`.

## 5. Confluence weights (inline, not yet externalized)

Source: [`ConfluenceEngine.java`](../../backend/src/main/java/com/delena/tradingportal/engine/confluence/ConfluenceEngine.java),
`decide()` score block (roughly lines 98–133):

| Condition | Weight |
|---|---|
| ICT sweep + reclaim | `+2` |
| ICT MSS in trade direction | `+2` |
| Active ICT entry zone present | `+1` |
| Premium/discount alignment | `+1` |
| Mode R + Gann angle alert | `+2` |
| Mode C + Gann trend | `+2` |
| So9 at-level | `+1` |
| Time-square near | `+1` |
| Killzone active | `+1` |
| Volume spike / reversal candle filter | `+1` |
| SMT confluence (gold-strong long / gold-weak short) | `+1` |
| SMT divergence against direction | `-1` |

Grade thresholds (`grade()`, lines 234–248): `A+` ≥ 7 (aligned), `A` ≥ 5 (aligned), `B` ≥ 3,
`C` ≥ 1, else `F`. Midday and non-aligned decisions are capped at grade `B`.
`trading.confluence.weights-version` (currently `v1` everywhere — see §6) is passed through as a
label only; it does not currently select between alternate weight sets (there is only one).

## 6. `application*.properties` — calibration-relevant keys

Source: [`application.properties`](../../backend/src/main/resources/application.properties) unless noted.

| Key | Default | Notes |
|---|---|---|
| `trading.confluence.weights-version` | `v1` | Label only today (§5). |
| `trading.style` | `DAY` | SCALP \| DAY \| POSITIONAL. |
| `trading.news.blackouts[*]` | *(unset in every profile)* | No NFP/FOMC/CPI windows configured yet — §14 open task. Format examples are commented in `application.properties` lines 40–46. |
| `trading.exec.live-enabled` | `false` | Stays `false` for all calibration work. |
| `trading.exec.broker` | `none` | Fails closed even if `live-enabled` flips. |
| `trading.seed.enabled` | `true` | Synthetic XAUUSD bars when `ohlc_candle` is empty — what a fresh DEV backtest run will use if MT5/ingest hasn't populated real bars. |
| `trading.security.dev-bypass` | `false` (`true` in `application-dev.properties`) | DEV-only fixed-header auth (`X-Dev-Token: dev-operator-token`) — see [`run-weight-sweep.ps1`](../../scripts/calibration/run-weight-sweep.ps1) usage. |

`application-dev.properties` ships `trading.security.dev-bypass=false` by default (line 27) —
CSS JWKS is the default path even on DEV. Flip it to `true` locally (or via
`application-local.properties`) to use the `X-Dev-Token` fixed-header shim documented in
[`run-api-dev.ps1`](../../scripts/run-api-dev.ps1) and used by
[`run-weight-sweep.ps1 -UseDevBypass`](../../scripts/calibration/run-weight-sweep.ps1) — otherwise
pass a real CSS bearer token to the sweep script with `-BearerToken`.

---

## Regenerating this snapshot

```powershell
# Quick diff check — did any of the source-of-truth files change since this snapshot?
git -C E:\MyWorkspace\trading-portal log -1 --format=%cd -- `
  backend/src/main/java/com/delena/tradingportal/engine/gann/GannConfig.java `
  backend/src/main/java/com/delena/tradingportal/engine/ict/IctConfig.java `
  backend/src/main/java/com/delena/tradingportal/engine/style/StyleRegistry.java `
  backend/src/main/java/com/delena/tradingportal/backtest/BacktestConfig.java `
  backend/src/main/java/com/delena/tradingportal/engine/confluence/ConfluenceEngine.java `
  backend/src/main/resources/application.properties
```

If any of those are newer than this file's "As of" date, re-read them and update the tables above.
