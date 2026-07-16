# Algorithm — Gann Cycle Engine (XAUUSD)

**Status:** Computable specification (v0 — no production code yet)  
**Theory SoT:** `docs/theory/GANN-INTRADAY-TIME-CYCLES.md`  
**Output consumer:** Confluence layer → automation pipeline  

**Disclaimer:** Geometric/cyclic scores are research aids. No profitability claim.

---

## 1. Goal

From OHLCV + pivot/origin selection, emit a **GannSnapshot** with 1×1 stretch, Square-of-9 proximity, time-squaring flags, session cycle checkpoints, and discrete quality — **no orders**.

---

## 2. Inputs

| Input | Type | Notes |
|-------|------|-------|
| `bars` | OHLCV[] | Entry TF: M5 or M15; must include `ny_time` |
| `bars_d1` | OHLCV[] | For PDH/PDL/prev close pivots |
| `asof` | timestamp | |
| `cfg` | Config | |
| `pivot_source` | enum | `NY_OPEN` (default), `LONDON_OPEN`, `PDH`, `PDL`, `PREV_CLOSE`, `NY_HIGH`, `NY_LOW`, … |

### 2.1 Config defaults

```text
atr_period        = 14
atr_alert         = 1.25
time_scale        = 1.0
near_time_min     = 5
so9_near_pct      = 0.0008   # 0.08%
so9_near_pts      = 0.5
so9_fine_steps    = [0.25, 0.5, 1.0]
so9_odd_n_max     = 4
milestones_min    = [45, 90, 180]
session_len_min   = 540      # 08:00–17:00 NY
cycle_fractions   = [0.125, 0.25, 0.333, 0.5, 0.75, 0.875]
vol_spike_mult    = 1.8
```

---

## 3. Outputs

```text
GannSnapshot {
  symbol: "XAUUSD"
  asof: instant
  pivot: { source, price, origin_ny }
  angle: {
    slope_1x1, equilibrium, deviation, stretch_atr
    bias: balanced|over_up|over_down
    alert: bool
    fan: { m1x1, m2x1, m1x2 }  // forward projections
  }
  so9: {
    levels: { kind, k, price, dist }[]
    at_level: bool
    nearest: level?
  }
  time_square: {
    minutes_elapsed
    price_move
    milestones: { m, target, near_time, near_price, near_square }[]
    any_near_square: bool
  }
  cycles: {
    session_fraction: float
    checkpoint: label|none
  }
  killzone: enum|none
  filters: { volume_spike, reversal_candle, rsi_div? }
  gann_bias: fade_long|fade_short|trend_long|trend_short|neutral
  quality: 0..5
  reasons: string[]
}
```

---

## 4. Pipeline steps

```text
1. Resolve pivot price + time origin from pivot_source
2. ATR + 1×1 equilibrium / stretch / fan
3. Build So9 level set; mark proximity
4. Compute time squaring vs milestones
5. Map session cycle fraction / checkpoint
6. Detect killzone + optional filters
7. Derive gann_bias (fade vs trend hint)
8. Score quality + reasons
9. Emit GannSnapshot
```

---

## 5. Pseudocode

```python
def compute_gann_snapshot(bars, bars_d1, asof, cfg, pivot_source) -> GannSnapshot:
    pivot = resolve_pivot(bars, bars_d1, pivot_source)  # price + origin_ny
    atr = atr_wilder(bars, cfg.atr_period)
    t = bars_since(bars, pivot.origin_ny, asof)
    px = close_at(bars, asof)

    slope = atr  # per bar on entry TF
    # sign_bias: +1 if studying upside equilibrium from open; use +1 from open always
    equilibrium = pivot.price + slope * t
    deviation = px - equilibrium
    stretch = deviation / atr if atr > 0 else 0.0
    angle_bias = classify_stretch(stretch, cfg.atr_alert)
    alert = abs(stretch) >= cfg.atr_alert

    levels = build_so9(pivot.price, cfg)
    at_level, nearest = proximity(px, levels, cfg)

    mins = minutes_between(pivot.origin_ny, asof)
    milestones = []
    for m in cfg.milestones_min:
        target = cfg.time_scale * m  # expected |move| heuristic
        near_t = abs(mins - m) <= cfg.near_time_min
        near_p = abs(abs(px - pivot.price) - target) <= so9_tol(pivot.price, cfg)
        near_sq = near_t or near_p or time_price_balance(mins, px, pivot, cfg)
        milestones.append(...)

    frac = mins / cfg.session_len_min
    checkpoint = nearest_fraction(frac, cfg.cycle_fractions)

    kz = detect_killzone(asof)
    filters = optional_filters(bars, cfg)
    gann_bias = infer_bias(angle_bias, at_level, milestones, kz)
    quality, reasons = score_gann(...)
    return GannSnapshot(...)
```

### 5.1 Square of 9 builder

```python
def build_so9(P, cfg):
    root = sqrt(P)
    levels = []
    for step in cfg.so9_fine_steps:
        for n in range(1, N_FINE):
            levels.append(Level("fine", +n*step, (root + n*step)**2))
            levels.append(Level("fine", -n*step, (root - n*step)**2))
    for n in range(1, cfg.so9_odd_n_max+1):
        levels.append(Level("odd", +2*n, (root + 2*n)**2))
        levels.append(Level("odd", -2*n, (root - 2*n)**2))
        levels.append(Level("even", +(2*n-1), (root + (2*n-1))**2))
        levels.append(Level("even", -(2*n-1), (root - (2*n-1))**2))
    return unique_sorted(levels)
```

**Convention freeze:** even steps use `(2n−1)` offsets — document in code comments when implemented.

### 5.2 Time–price balance

```python
def time_price_balance(mins, px, pivot, cfg):
    expected = cfg.time_scale * mins
    return abs(abs(px - pivot.price) - expected) <= tol(pivot.price, cfg)
```

### 5.3 Bias inference (hint only)

```python
def infer_bias(angle_bias, at_level, milestones, kz):
    if angle_bias == "over_up" and (at_level or any_near_square(milestones)):
        return "fade_short"
    if angle_bias == "over_down" and (at_level or any_near_square(milestones)):
        return "fade_long"
    if angle_bias == "balanced" and kz:
        return "neutral"  # confluence may still use trend hold vs ICT
    # trend hints: mild stretch with price above rising eq → trend_long, etc.
    return "neutral"
```

Confluence decides whether fade is allowed (Mode R) or suppressed (Mode C).

### 5.4 Scoring (0–5)

| Factor | +pts |
|--------|------|
| Stretch alert | +2 |
| At So9 | +1 |
| Any near_square | +1 |
| Killzone active | +1 |
| Volume spike or reversal candle | +1 |

Clamp 0..5.

---

## 6. Reason codes

```text
ANG_OVER_UP | ANG_OVER_DOWN | ANG_BALANCED | ANG_ALERT
SO9_FINE | SO9_ODD | SO9_EVEN
TSQ_45 | TSQ_90 | TSQ_180 | TSQ_NEAR
CYC_1_4 | CYC_1_2 | CYC_3_4 | ...
KZ_* | VOL_SPIKE | REV_CANDLE | RSI_DIV
PIVOT_NY_OPEN | PIVOT_PDH | ...
```

---

## 7. Multi-day cycle module (observe-only v1)

```text
SwingCycleOverlay {
  origin_swing: { price, date }
  day_counts: [3, 7, 14, 21]
  hits: { day_count, date, active_today: bool }[]
}
```

Emit beside intraday snapshot; **do not** increment automation quality from these until research sign-off.

---

## 8. Failure modes

| Case | Behavior |
|------|----------|
| Cannot resolve pivot (empty session) | quality 0, `DATA_GAP` |
| ATR = 0 | skip stretch; neutral |
| Outside session | still compute levels; killzone none; lower quality |

---

## 9. Test vectors (paper)

1. Synthetic: linear rise at 1× ATR/bar → stretch ~0, balanced.  
2. Jump +1.5 ATR above eq at So9 fine → `ANG_ALERT` + `SO9_FINE`, bias `fade_short`.  
3. mins=90, abs move ≈ 90×time_scale → `TSQ_90` near_square.  
4. Pivot PDH vs NY_OPEN produces different So9 grids — both valid; confluence uses declared primary.

---

## 10. Assumptions & open questions

- ATR-per-bar 1×1 is v1 standard.  
- Even-square offset convention `(2n−1)` frozen as above.  
- Calibration of `time_scale` by ATR quartile is required before live.  
- Dual-origin fans (London + NY) deferred to v2 panel UI.

---

## 11. Related

- Theory: `docs/theory/GANN-INTRADAY-TIME-CYCLES.md`  
- Fusion: `docs/theory/CONFLUENCE-FRAMEWORK.md`  
- Sibling: `ICT-SIGNAL-ENGINE.md`  
- Deep formulas / So9 / stretch / roadmap: [`DEEP-ALGORITHMS-AND-CALCULATIONS.md`](./DEEP-ALGORITHMS-AND-CALCULATIONS.md)  
- Reference only: grok_dev Gann Intraday usage guide
