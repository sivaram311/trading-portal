# Confluence Framework — ICT × Gann (XAUUSD)

**Status:** Decision-layer SoT (v0)  
**Instrument:** XAUUSD  
**Disclaimer:** Confluence improves **selection quality**, not certainty. No profitability claim. Research → paper → live gates.

---

## 1. Purpose

Define how **ICT** and **Gann** combine into a single **decision object** for the portal and (later) automation.

Siloed tools (ICT-only or Gann-only) are allowed for research views; **actionable automation** should prefer confluence grades defined here.

---

## 2. Design principles

1. **Independent evidence** — ICT and Gann must be computed separately, then fused (no circular features).  
2. **Direction first** — agree on long/short/flat before sizing.  
3. **Time gate** — killzones raise score; midday suppresses new risk.  
4. **Invalidation is mandatory** — every actionable idea carries a hard stop level.  
5. **Explainability** — every score emits human-readable reason codes for the journal/UI.  
6. **Fail closed** — missing data, news veto, or conflicting HTF bias → `NO_TRADE`.

---

## 3. Inputs (from engines)

### From ICT engine

| Field | Example |
|-------|---------|
| `ict_bias` | `long` \| `short` \| `neutral` |
| `structure_event` | `MSS` \| `BOS` \| `none` |
| `liquidity_event` | `sweep_AHH` \| `sweep_PDL` \| … |
| `entry_zone` | OB and/or FVG band |
| `premium_discount` | `premium` \| `discount` \| `EQ` |
| `ict_quality` | 0–5 discrete |

### From Gann engine

| Field | Example |
|-------|---------|
| `gann_bias` | `fade_long` \| `fade_short` \| `trend_long` \| `trend_short` \| `neutral` |
| `stretch_atr` | float |
| `at_so9` | bool + level id |
| `near_square` | bool + milestone |
| `angle_relation` | `above_1x1` \| `below_1x1` \| `at_1x1` |
| `gann_quality` | 0–5 discrete |

### Shared context

| Field | Example |
|-------|---------|
| `killzone` | `NY_OPEN` \| `LONDON_OPEN` \| … \| `none` |
| `htf_bias` | from H1/H4 ICT dealing range |
| `news_veto` | bool |
| `session_date` | trading day id |

---

## 4. Direction agreement matrix

| ICT bias | Gann mode | Result |
|----------|-----------|--------|
| long | fade_long or trend_long | **ALIGN_LONG** |
| short | fade_short or trend_short | **ALIGN_SHORT** |
| long | fade_short / trend_short | **CONFLICT** → NO_TRADE (or research-only) |
| short | fade_long / trend_long | **CONFLICT** → NO_TRADE |
| either | neutral | **SOFT** — allow only if ICT quality ≥ 4 and killzone |
| neutral | either | **SOFT** — Gann watch only unless ICT prints MSS+sweep |

**Important:** Gann **fade** against ICT **trend continuation** is a common conflict — default **NO_TRADE** for automation; show as “research divergence” in UI.

---

## 5. Narrative modes

Confluence selects a **mode** so risk rules differ.

### Mode R — Reversal (raid + square)

**Intent:** Fade a liquidity grab at geometric exhaustion.

**Required (minimum):**

1. ICT liquidity sweep + MSS (or strong displacement reclaim)  
2. Killzone active  
3. At least two of: `at_so9`, `near_square`, `stretch_atr ≥ atr_alert`  
4. Entry in premium (for shorts) or discount (for longs) relative to dealing range  

**Preferred Gann:** fade_* aligned with MSS direction.

### Mode C — Continuation (structure + angle hold)

**Intent:** Join displacement after pullback.

**Required:**

1. ICT BOS + pullback to OB/FVG  
2. Price holds Gann 1×1 or 1×2 in trend direction  
3. Killzone or early overlap  
4. HTF bias aligned  

**Preferred Gann:** trend_* ; stretch fade signals are **ignored** or inverted to “wait for pullback complete.”

### Mode T — Time-triggered watch

**Intent:** Elevate monitoring at cycle milestones; not auto-entry alone.

**Required:** `near_square` or session division hit + one ICT or Gann quality ≥ 3.  
**Automation:** alert only unless Mode R/C also satisfied.

---

## 6. Scoring model (v1 proposal)

### 6.1 Base points

| Factor | Points | Cap |
|--------|--------|-----|
| ICT sweep + structure reclaim | +2 | |
| ICT MSS aligned | +2 | |
| ICT OB/FVG entry zone touch | +1 | |
| Premium/discount alignment | +1 | |
| Gann stretch ≥ atr_alert (Mode R) | +2 | |
| Gann hold of 1×1 (Mode C) | +2 | |
| At So9 level | +1 | |
| Near time square | +1 | |
| Active priority killzone | +1 | |
| Volume / reversal candle (optional) | +1 | |

**Maximum theoretical:** ~12; practical A+ setups usually 6–9.

### 6.2 Grades

| Grade | Score | Automation policy (proposed) |
|-------|-------|------------------------------|
| **A+** | ≥ 7 and ALIGN_* | Eligible for paper auto; live only after gates |
| **A** | 5–6 and ALIGN_* | Semi-auto / operator confirm |
| **B** | 3–4 | Alert only |
| **C** | 1–2 | Journal / ignore |
| **F** | CONFLICT, news_veto, or midday new-entry | NO_TRADE |

### 6.3 Soft penalties

| Condition | Adjustment |
|-----------|------------|
| Midday window | force grade ≤ B for new entries |
| HTF opposing | −2 or NO_TRADE if strong oppose |
| Spread > threshold | NO_TRADE |
| Duplicate signal within N minutes | suppress |

---

## 7. Decision object (output contract)

```text
ConfluenceDecision {
  id: string
  symbol: "XAUUSD"
  ts: instant
  mode: R | C | T | NONE
  direction: long | short | flat
  grade: A+ | A | B | C | F
  score: number
  reasons: string[]          // e.g. "ICT_SWEEP_ALH", "GANN_SO9", "KZ_NY_OPEN"
  entry: { type, low, high }
  stop: price
  targets: price[]           // T1 EQ/1x1, T2 opposite liquidity / So9
  invalid_if: string[]
  engines: { ict_ref, gann_ref }
  automation: allow | confirm | deny
}
```

This object is the **single** handoff into `docs/algorithms/AUTOMATION-PIPELINE.md`.

---

## 8. Target & stop conventions

| Mode | Stop | T1 | T2 |
|------|------|----|----|
| R | Beyond sweep extreme (+ buffer) | Dealing EQ **or** Gann 1×1 equilibrium ( nearer first ) | Opposite session liquidity / next major So9 |
| C | Beyond OB invalidation | Prior swing / FVG CE | Session extreme / PDH-PDL |
| T | n/a until promotes to R/C | — | — |

**Conflict rule:** If ICT stop and Gann stop disagree, use the **wider** stop for safety in paper, then measure R-multiple drag — open research which is optimal.

---

## 9. Worked examples (illustrative)

### Example 1 — A+ Mode R (short)

- NY Open; sweeps ALH then closes back  
- M15 bearish MSS  
- Stretch +1.4× ATR above 1×1 from NY open  
- Tags So9 fine level; 90m NEAR_SQUARE  
- Premium vs H1 range  

→ ALIGN_SHORT, score ~8, automation `confirm` until live gate.

### Example 2 — Conflict (no trade)

- ICT bullish BOS continuation pullback  
- Gann fade_short solely from stretch without sweep/MSS  

→ CONFLICT / Mode NONE for auto; UI shows divergence.

### Example 3 — Mode C long

- London BOS up; NY Open holds bullish OB  
- Price above rising 1×1; So9 support bounce  
- Discount on H1  

→ ALIGN_LONG continuation; ignore raw “overextended” fade score.

---

## 10. Operator UI expectations (portal)

1. **Headline grade** + mode + direction  
2. Reason chips from both engines  
3. Levels: entry zone, stop, T1/T2, 1×1, So9, liquidity pools  
4. Explicit **NO_TRADE** state with cause  
5. One-click journal capture of the decision object  

Phone-first viewport later: 360×780 primary (machine device SoT).

---

## 11. Assumptions & open questions

### Assumptions

- Fusion after independent engines is mandatory.  
- CONFLICT defaults to no automation.  
- Killzone gating applies to new risk, not to managing open paper trades.  
- Grades are tunable; freeze weights before any live micro.

### Open questions

1. Optimal weights via walk-forward on labeled gold history.  
2. Whether Mode C should require So9 or only angle hold.  
3. Single “equilibrium” target: prefer ICT EQ vs Gann 1×1 when both exist.  
4. News veto list and blackout minutes.  
5. Correlation of grade vs realized expectancy (must be measured, not assumed).

---

## 12. Related docs

| Doc | Role |
|-----|------|
| [`ICT-GOLD.md`](./ICT-GOLD.md) | ICT definitions |
| [`GANN-INTRADAY-TIME-CYCLES.md`](./GANN-INTRADAY-TIME-CYCLES.md) | Gann definitions |
| [`../algorithms/ICT-SIGNAL-ENGINE.md`](../algorithms/ICT-SIGNAL-ENGINE.md) | ICT compute |
| [`../algorithms/GANN-CYCLE-ENGINE.md`](../algorithms/GANN-CYCLE-ENGINE.md) | Gann compute |
| [`../algorithms/AUTOMATION-PIPELINE.md`](../algorithms/AUTOMATION-PIPELINE.md) | Signal → risk → execution sketch |
| [`../algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md`](../algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md) | Precise formulas, OTE, sizing, backtest, roadmap |
