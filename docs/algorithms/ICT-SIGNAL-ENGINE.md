# Algorithm — ICT Signal Engine (XAUUSD)

**Status:** Computable specification (v0 — no production code yet)  
**Theory SoT:** `docs/theory/ICT-GOLD.md`  
**Output consumer:** Confluence layer → automation pipeline  

**Disclaimer:** Spec for research/paper systems. Not financial advice; no profitability claim.

---

## 1. Goal

Given OHLCV bars (multi-TF) and session metadata, emit an **IctSnapshot** describing structure, liquidity, zones, and a discrete quality score — **without** placing orders.

---

## 2. Inputs

| Input | Type | Notes |
|-------|------|-------|
| `bars_h1`, `bars_m15`, `bars_m5` | OHLCV[] | Broker time + `ny_time` enriched |
| `asof` | timestamp | Evaluation instant |
| `cfg` | Config | thresholds below |
| `calendar` | optional | news blackout windows |

### 2.1 Config defaults (v1)

```text
swing_n_m15            = 2
swing_n_h1             = 3
equal_eps_pts          = 0.6
sweep_reclaim_bars     = 3
min_fvg_atr_frac       = 0.5
min_fvg_pts            = 0.8
displacement_body_frac = 0.60
vol_ma_period          = 20
vol_spike_mult         = 1.5
killzones              = [LONDON_OPEN, NY_OPEN, NY_AFTERNOON]
```

---

## 3. Outputs

```text
IctSnapshot {
  symbol: "XAUUSD"
  asof: instant
  killzone: enum | none
  htf: {
    dealing_low, dealing_high, eq
    premium_discount: premium|discount|EQ
    bias: long|short|neutral
  }
  structure: {
    swings: Swing[]
    event: BOS|MSS|none
    direction: long|short|none
    displacement: bool
  }
  liquidity: {
    pools: { name, price, side }[]
    event: sweep|none
    swept_pool: name?
    reclaim: bool
  }
  zones: {
    order_blocks: Zone[]
    fvgs: Zone[]
    active_entry: Zone?
  }
  quality: 0..5
  reasons: string[]
  raw_refs: { bar_ids... }
}
```

---

## 4. Pipeline steps

```text
1. Enrich / validate bars (ny_time present, monotonic)
2. Detect killzone(asof)
3. Build HTF dealing range + premium/discount (H1)
4. Detect swings on M15 (+ H1 for bias)
5. Detect displacement candles on M15/M5
6. Classify BOS / MSS
7. Build liquidity pools (PDH/PDL, AHH/ALH, session, EQH/EQL, rounds)
8. Detect sweep + reclaim
9. Derive order blocks + FVGs
10. Select active entry zone (if any)
11. Score quality + reasons
12. Emit IctSnapshot
```

---

## 5. Pseudocode

```python
def compute_ict_snapshot(bars_h1, bars_m15, bars_m5, asof, cfg) -> IctSnapshot:
    assert has_ny_time(bars_m15)
    kz = detect_killzone(asof, cfg.killzones)

    htf = dealing_range(swings(bars_h1, cfg.swing_n_h1))
    htf.premium_discount = locate_price(close(bars_m15, asof), htf)
    htf.bias = bias_from_structure(bars_h1)  # HH/HL vs LH/LL

    swings_m15 = swings(bars_m15, cfg.swing_n_m15)
    disp = find_displacement(bars_m15, cfg)
    structure = classify_bos_mss(swings_m15, bars_m15, htf.bias, disp)

    pools = build_pools(bars_m15, bars_h1, cfg)
    liq = detect_sweep_reclaim(bars_m15, pools, cfg.sweep_reclaim_bars)

    obs = derive_order_blocks(bars_m15, structure, disp)
    fvgs = detect_fvgs(bars_m15, cfg)
    entry = select_entry_zone(structure, liq, obs, fvgs, htf)

    quality, reasons = score_ict(kz, structure, liq, entry, htf, disp)
    return IctSnapshot(...)
```

### 5.1 Swings

```python
def is_swing_high(bars, i, n):
    return all(bars[i].high > bars[i-j].high for j in range(1, n+1)) \
       and all(bars[i].high > bars[i+j].high for j in range(1, n+1))
# symmetric for swing_low
```

### 5.2 BOS / MSS

```python
def classify_bos_mss(swings, bars, prior_bias, disp):
    last_close = bars[-1].close
    sh, sl = last_swing_high(swings), last_swing_low(swings)
    if prior_bias == "long" and last_close > sh.price and disp:
        return Event("BOS", "long")
    if prior_bias == "long" and last_close < sl.price and disp:
        return Event("MSS", "short")
    # symmetric for prior_bias short / neutral heuristics...
    return Event("none", "none")
```

### 5.3 Sweep + reclaim

```python
def detect_sweep_reclaim(bars, pools, k):
    for pool in priority_sort(pools):  # AHH/ALH, PDH/PDL first
        for i in recent_bars(bars, lookback=20):
            if trades_beyond(bars[i], pool):
                if closes_back_inside(bars[i:i+k], pool):
                    return Sweep(pool, reclaim=True, bar=i)
    return Sweep.none()
```

### 5.4 FVG

```python
def detect_fvgs(bars, cfg):
    out = []
    for i in range(2, len(bars)):
        if bars[i].low > bars[i-2].high:
            gap = Gap(bars[i-2].high, bars[i].low, "bull")
        elif bars[i].high < bars[i-2].low:
            gap = Gap(bars[i].high, bars[i-2].low, "bear")
        else:
            continue
        if gap.size >= max(cfg.min_fvg_pts, cfg.min_fvg_atr_frac * atr(bars, 14)):
            out.append(gap)
    return mark_fill_state(out, bars)
```

### 5.5 Scoring (0–5)

| Condition | +pts |
|-----------|------|
| Killzone active | +1 |
| Sweep + reclaim | +2 |
| MSS aligned with intended trade | +2 |
| BOS only (continuation path) | +1 |
| Displacement true | +1 |
| Valid OB or FVG entry zone | +1 |
| PD alignment (discount for long / premium for short) | +1 |

Clamp to 0..5. Map intended trade from structure direction after sweep.

---

## 6. Reason codes (stable strings)

```text
KZ_LONDON_OPEN | KZ_NY_OPEN | KZ_NY_AFTERNOON
SWEEP_AHH | SWEEP_ALH | SWEEP_PDH | SWEEP_PDL | SWEEP_EQH | SWEEP_EQL
MSS_BULL | MSS_BEAR | BOS_BULL | BOS_BEAR
DISP_OK | OB_ACTIVE | FVG_ACTIVE
PD_DISCOUNT | PD_PREMIUM | PD_EQ
NEWS_VETO | DATA_GAP
```

---

## 7. Failure modes

| Case | Behavior |
|------|----------|
| Missing `ny_time` | Emit empty snapshot + `DATA_GAP`; deny automation |
| Flat / holiday session | quality 0 |
| Sweep without reclaim in *k* bars | liquidity.event may be `sweep` with reclaim=false; quality capped |
| Conflicting H1 vs M15 | prefer H1 bias; flag `HTF_CONFLICT` in reasons |

---

## 8. Test vectors (paper)

1. Synthetic M15: Asia range → London sweep AHH → reclaim → bearish MSS → FVG. Expect quality ≥ 4, `SWEEP_AHH`, `MSS_BEAR`.  
2. Wick beyond PDL without close reclaim → no high-quality reversal.  
3. Midday MSS without killzone → quality ≤ 3.  
4. Tiny FVG < min size → ignored.

---

## 9. Assumptions & open questions

- Close-based structure only for v1.  
- RSI **not** required (unlike some grok_dev NY liquidity presets).  
- Optimal `equal_eps_pts` by ATR regime TBD.  
- Whether M1 entry refinement is mandatory before live — likely yes for execution adapter later.

---

## 10. Related

- Theory: `docs/theory/ICT-GOLD.md`  
- Fusion: `docs/theory/CONFLUENCE-FRAMEWORK.md`  
- Sibling: `GANN-CYCLE-ENGINE.md`
