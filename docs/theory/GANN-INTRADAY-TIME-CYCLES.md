# W.D. Gann — Intraday & Time Cycles (XAUUSD)

**Status:** Definition-grade SoT (v0 research)  
**Instrument:** XAUUSD / spot gold  
**Audience:** Quant / strategy + engine implementers  
**Disclaimer:** Geometric and cyclic tools are **decision aids**, not oracles. No guaranteed edge. Research → paper → live gates apply.

---

## 1. Purpose

Define Gann concepts usable for **intraday gold** and **time-cycle** analysis, precise enough to implement `docs/algorithms/GANN-CYCLE-ENGINE.md`.

Ideas seeded from prior grok_dev Gann Intraday work (1×1 angles, Square of 9, time squaring, killzones) — rewritten here as this project’s SoT.

---

## 2. Philosophy (operational, not mystical)

Gann methods used in this portal reduce to three computable claims:

1. **Price geometry** — from an origin, certain slopes (angles) act as equilibrium / stretch references.  
2. **Price vibration** — around a pivot, Square-of-9 (and related) levels act as magnets / reaction zones.  
3. **Time–price balance** — when elapsed time and price travel “square,” reaction probability rises (still probabilistic).

Everything else (seasonals, planetary) is **out of scope for v1**.

---

## 3. Anchors & origins

Every Gann calculation needs a **pivot** (price) and often a **time origin** (bar / clock).

### 3.1 Recommended pivots (gold intraday)

| Pivot | Definition | Typical use |
|-------|------------|-------------|
| NY open | First M15 open at/after 08:00 NY | Default session study |
| London open | First M15 in 03:00–05:00 NY | Early expansion geometry |
| PDH / PDL | Prior D1 high / low | Structure magnets |
| Prev close | Prior D1 close | Gap / balance reference |
| Session high/low | Running London or NY extreme | Dynamic So9 center |
| Swing pivot | Last confirmed SH/SL | Cycle / angle restart |

**Assumption:** Operator (or automation) selects one primary pivot per study; engine may compute a **panel** of pivots but confluence uses a declared primary.

### 3.2 Time origin

Default: **NY session open** (08:00 America/New_York).  
Alternative: London open for London-only studies.

All cycle minutes are measured from the chosen origin with DST-aware NY clock.

---

## 4. Gann angles (intraday 1×1 family)

### 4.1 Classic ratios

| Angle | Price:Time idea | Intraday proxy |
|-------|-----------------|----------------|
| **1×1** | Unit price per unit time | Slope = ATR(14) per bar on entry TF |
| **2×1** | Steeper | 2 × ATR per bar |
| **1×2** | Shallower | 0.5 × ATR per bar |

**Why ATR?** Absolute “1 point per minute” is meaningless across gold volatility regimes. ATR-normalized 1×1 matches the grok_dev practical approach and is **assumed for v1**.

### 4.2 Equilibrium & stretch

From origin bar index `0` with price `P0`:

```
equilibrium[t] = P0 + (sign_bias * slope_1x1 * t)
deviation[t]   = price[t] - equilibrium[t]
stretch_atr    = deviation[t] / ATR(14)
```

| State | Condition (default) |
|-------|---------------------|
| Balanced | \|stretch_atr\| < 0.75 |
| Extended | 0.75 ≤ \|stretch_atr\| < atr_alert |
| Alert / overextended | \|stretch_atr\| ≥ atr_alert (default **1.25**) |

**Trading idea (mean reversion):** Fade stretch toward equilibrium **when** killzone + So9/time-square confluence exist.  
**Trading idea (trend):** Use 1×1 / 2×1 as **trail / support** when ICT structure is trending — do not auto-fade every stretch.

### 4.3 Fan projection

Project next *H* bars of 1×1, 2×1, 1×2 as target / invalidation references (not entries alone).

---

## 5. Square of Nine (So9)

### 5.1 Core transform

Treat price as on a spiral rooted at pivot `P`:

```
root = sqrt(P)
level(k) = (root + k)²     // k in steps of ±0.25, ±0.5, ±1.0, …
```

### 5.2 Level families (v1)

| Family | Step in √price space | Approx degree hint |
|--------|----------------------|--------------------|
| Fine 45° | ±0.25 | Quarter-turn feel |
| Fine 90° | ±0.5 | Half-cardinal |
| Fine 180° | ±1.0 | Opposition |
| Odd squares | ±2n on √scale | Stronger magnets |
| Even squares | ±(2n±1) | Alternate magnets |

**Near-level flag:** price within `max(0.08% of P, 0.5 points)` of a level (tunable).

### 5.3 Odd / even square (operational)

- **Odd:** `(√P ± 2n)²`  
- **Even:** `(√P ± (2n − 1))²` or `(√P ± (2n + 1))²` — pick one convention and freeze it in the engine (document the choice in algorithm doc).

**Assumption:** So9 is a **reaction grid**, not a standalone long/short signal.

---

## 6. Time squaring (intraday)

### 6.1 Idea

Gann: when **time traveled** and **price traveled** are in balance, turning points cluster.

### 6.2 Session milestones

From NY open, mark **45 / 90 / 180** minutes (optional: 360 for full-session studies).

```
minutes_elapsed = now_ny - origin_ny
price_move      = price_now - P_origin
abs_move        = |price_move|
target_move     = minutes_elapsed * time_scale   // time_scale default 1.0
```

| Flag | Condition (defaults) |
|------|----------------------|
| NEAR_TIME | within 5 minutes of 45/90/180 |
| NEAR_PRICE | abs_move within tolerance of milestone target |
| NEAR_SQUARE | time–price equality within scaled tolerance **or** both near flags |

**time_scale** (0.5–2.0): compresses/expands expected points-per-minute. Gold often needs calibration per ATR regime — **open research**.

### 6.3 Bar-count squaring (alternate)

On M5/M15, square **bar count** from origin against ATR units:

```
bars * ATR ≈ |price_move|
```

Useful when clock milestones disagree with bar structure (holidays, delayed open).

---

## 7. Time cycles (beyond single session)

### 7.1 Intraday cycle divisions

Divide the active session (e.g. 08:00–17:00 = 540 min) into:

| Division | Meaning |
|----------|---------|
| 1/8, 1/4, 1/3, 1/2 | Time harmonic checkpoints |
| 3/4, 7/8 | Late-session acceleration / exhaustion watches |

At each checkpoint, **re-score** stretch, So9 proximity, and ICT structure — do not auto-trade the clock alone.

### 7.2 Multi-day cycles (research tier)

| Cycle | Use on gold | v1 status |
|-------|-------------|-----------|
| 3 / 7 / 14 / 21 trading days from swing | Swing timing overlays | Observe / journal only |
| Anniversary of major highs/lows | Macro awareness | Manual annotation |
| Square of 9 **time** (degrees → days) | Advanced | Deferred |

**Assumption:** Automation v1 is **intraday-first**; multi-day cycles feed HTF bias labels, not market orders.

### 7.3 Weekly / monthly (portal later)

Portal may show static cycle calendars; engines should not block on unfinished calendar features.

---

## 8. Killzones (shared with ICT)

Gann confluence historically scored higher inside:

| Zone | NY window |
|------|-----------|
| London Open | 03:00–05:00 |
| NY Open | 08:00–10:00 |
| NY Overlap | 08:00–11:00 |
| NY Afternoon | 14:00–17:00 |

**Rule:** Killzone is a **multiplier / gate**, not a signal.

---

## 9. Supporting filters (optional)

| Filter | Role |
|--------|------|
| Reversal candle (pin / engulf) | Timing confirmation |
| Volume spike vs 20-bar avg | Participation |
| RSI divergence (entry TF) | Soft confirmation — **not** core Gann |

These mirrored grok_dev scoring; this project may reweight them under the confluence framework.

---

## 10. Setup archetypes

### A. Stretch fade at So9 (mean reversion)

1. Active killzone  
2. \|stretch_atr\| ≥ atr_alert from 1×1  
3. Price tags So9 fine or odd/even level  
4. Optional: NEAR_SQUARE  
5. Target: 1×1 equilibrium; stop beyond next So9 ring  

### B. Trend ride along angle

1. ICT bullish structure (BOS)  
2. Pullback holds 1×1 or 1×2 from NY open  
3. So9 level acts as support, not fade trigger  
4. Trail under rising 1×1  

### C. Time-square reversal watch

1. 90 or 180 min NEAR_SQUARE  
2. Stretch against the session move  
3. Wait for ICT MSS or sweep reclaim before entry  

---

## 11. What we deliberately exclude (v1)

- Planetary / astrology overlays  
- Manual “cardinal cross” charting without price feed  
- Guaranteeing reversals at every So9 print  
- Using Gann alone to size live risk  

---

## 12. Assumptions & open research questions

### Assumptions

- ATR-based 1×1 is the correct modern proxy for intraday gold.  
- NY open is the default origin for US-session studies.  
- So9 near-level tolerance scales with price level.  
- Multi-day cycles are labels for humans/HTF, not v1 auto-entries.

### Open questions

1. Best `time_scale` by ATR quartile (calibrate on paper history).  
2. Whether London-open 1×1 should override NY-open after 08:00 or run dual fans.  
3. Odd vs even square predictive contribution — A/B in journal.  
4. Mapping Square-of-9 **degrees** to minutes for gold specifically.  
5. Interaction: ICT dealing-range EQ vs Gann 1×1 equilibrium — often close but not identical (confluence doc).

---

## 13. Related docs

| Doc | Role |
|-----|------|
| [`ICT-GOLD.md`](./ICT-GOLD.md) | Liquidity / structure theory |
| [`CONFLUENCE-FRAMEWORK.md`](./CONFLUENCE-FRAMEWORK.md) | Fusion rules |
| [`../algorithms/GANN-CYCLE-ENGINE.md`](../algorithms/GANN-CYCLE-ENGINE.md) | Computable engine |
| [`../reference/GROK-DEV.md`](../reference/GROK-DEV.md) | Prior Gann Intraday pointer |
