# Deep Algorithms & Calculations — XAUUSD (ICT + Gann + Multi-Factor)

**Status:** Implementation-ready (v1)  
**Symbol:** XAUUSD only  
**Audience:** Cursor AI / developers implementing engines, backtester, risk, UI  
**Depends on:**
- `docs/algorithms/ICT-SIGNAL-ENGINE.md`
- `docs/algorithms/GANN-CYCLE-ENGINE.md`
- `docs/theory/CONFLUENCE-FRAMEWORK.md`
- `docs/algorithms/AUTOMATION-PIPELINE.md`
- Theory SoT: `docs/theory/ICT-GOLD.md`, `docs/theory/GANN-INTRADAY-TIME-CYCLES.md`

**Disclaimer:** Research/paper only. No profitability guarantees. No live execution until promotion gates pass.

---

## 0. Current Codebase Map (Ground Truth)

Living Java engines already exist under `backend/src/main/java/com/delena/tradingportal/`. Use this table before inventing parallel modules.

| Module | Path | Status |
|--------|------|--------|
| ICT engine | `engine/ict/IctEngine.java` | **Done (v1):** swings, BOS/MSS, AHH/ALH/PDH/PDL, sweep+reclaim, OB, FVG, PD, quality |
| ICT config | `engine/ict/IctConfig.java` | Done |
| Gann engine | `engine/gann/GannEngine.java` | **Done (v1):** ATR 1×1, So9, time square, cycles, bias |
| Gann config | `engine/gann/GannConfig.java` | Done |
| Confluence | `engine/confluence/ConfluenceEngine.java` | **Done (v1):** modes R/C/T, grades, stops/targets |
| Risk gate | `engine/risk/RiskGate.java` | **Done (MVP):** size, 1 position, −2R day, news, midday |
| Pipeline | `pipeline/PipelineService.java` | Done |
| Paper trading | `paper/PaperTradingService.java` | **Basic:** confirm / auto A+ / dismiss; no BE/partial/trail/add |
| NY time | `common/NyTime.java` | Done — never fall back to UTC |
| News calendar | `news/NewsCalendarService.java` | Stub / expand |
| Backtester | `backtest/Backtester.java` | **Done (v1):** bar-by-bar + metrics CSV; WF/MC deferred |
| OTE calculator | `engine/ict/OteCalculator.java` | **Done** — wired into `selectEntry` |
| EQH/EQL + rounds | `engine/ict/LiquidityPools.java` | **Done** — appended after AHH/ALH/PDH/PDL |
| Style profiles | `engine/style/*` | **Done** — SCALP/DAY/POSITIONAL; `trading.style` |
| PositionManager | `paper/PositionManager.java` | **Done** — BE/T1/trail; **no ADD_LEG** (Grok MAX_LEGS=1) |
| MarketQualityGate | `engine/risk/MarketQualityGate.java` | **Done** — Pipeline merge before persist |
| Breaker / IFVG | `IctEngine.deriveBreakers/Ifvgs` | **Done** — Zones.breakers/ifvgs + UNICORN selectEntry |
| Walk-forward / Monte-Carlo | `Backtester.walkForward` / `monteCarlo` | **Done** |
| Angular overlays | `price-levels` + confluence fetch | **Done** — OB/FVG/BREAKER/IFVG/OTE/So9/1×1 |
| DXY SMT | `engine/smt/SmtDetector.java` | **Done** — DXY bars via `MarketDataService`; soft-fail when missing |

**Rule for Cursor:** Prefer extending existing classes and models (`IctSnapshot`, `GannSnapshot`, `ConfluenceDecision`, `RiskVerdict`) over greenfield rewrites.

---

## 1. Shared Utilities (Implement / Reuse First)

### 1.1 ATR (Wilder)

```java
double atr(List<OhlcBar> bars, int period) {
    if (bars.size() < period + 1) return 0.0;
    double trSum = 0;
    for (int i = bars.size() - period; i < bars.size(); i++) {
        OhlcBar b = bars.get(i);
        OhlcBar prev = bars.get(i - 1);
        double tr = Math.max(b.high() - b.low(),
                Math.max(Math.abs(b.high() - prev.close()),
                         Math.abs(b.low() - prev.close())));
        trSum += tr;
    }
    return trSum / period;
}
```

Production note: prefer true Wilder smoothing (seed SMA then recursive) when enough history exists; the simple mean above is acceptable for MVP proximity/stretch.

### 1.2 Swing Detection

```java
boolean isSwingHigh(List<OhlcBar> bars, int i, int n) {
    if (i < n || i + n >= bars.size()) return false;
    double h = bars.get(i).high();
    for (int j = 1; j <= n; j++) {
        if (bars.get(i - j).high() >= h || bars.get(i + j).high() >= h) return false;
    }
    return true;
}
// Symmetric isSwingLow using .low() and <=
```

Defaults: `swing_n_m15 = 2`, `swing_n_h1 = 3`.

### 1.3 NY Time Helpers (reuse `NyTime.java`)

- Convert broker timestamp → `America/New_York`.
- Killzone flags: `LONDON_OPEN` (03:00–05:00), `NY_OPEN` (08:00–10:00), `NY_AFTERNOON` (14:00–17:00).
- **Fail closed** if `ny_time` missing (`DATA_GAP`).

### 1.4 Volume Spike

```java
boolean isVolumeSpike(List<OhlcBar> bars, int i, int maPeriod, double mult) {
    double volMa = averageVolume(bars, i - maPeriod, i);
    return bars.get(i).volume() >= volMa * mult;  // ICT default 1.5; Gann filter 1.8
}
```

Gold often has tick volume only — treat as relative confirmation, not absolute liquidity.

### 1.5 Volatility Regime

```java
String regime(double atrCurrent, double atrLong) {
    double ratio = atrCurrent / atrLong;
    if (ratio > 1.5) return "HIGH_VOL";
    if (ratio < 0.7) return "LOW_VOL";
    return "NORMAL";
}
```

Scale `min_fvg_*`, stop buffers, and position size by regime.

---

## 2. Enhanced ICT Algorithms

### 2.1 Fair Value Gap (FVG) — Precise

```java
List<Fvg> detectFvgs(List<OhlcBar> bars, double minPts, double minAtrFrac) {
    List<Fvg> fvgs = new ArrayList<>();
    double atr = atr(bars, 14);
    double minSize = Math.max(minPts, minAtrFrac * atr);  // defaults 0.8 pts, 0.5*ATR

    for (int i = 2; i < bars.size(); i++) {
        OhlcBar c0 = bars.get(i - 2), c1 = bars.get(i - 1), c2 = bars.get(i);
        // Bullish FVG
        if (c2.low() > c0.high()) {
            double size = c2.low() - c0.high();
            if (size >= minSize) {
                fvgs.add(new Fvg(c0.high(), c2.low(), "BULL", i, midpoint(c0.high(), c2.low())));
            }
        }
        // Bearish FVG
        if (c2.high() < c0.low()) {
            double size = c0.low() - c2.high();
            if (size >= minSize) {
                fvgs.add(new Fvg(c2.high(), c0.low(), "BEAR", i, midpoint(c2.high(), c0.low())));
            }
        }
    }
    return markFillStates(fvgs, bars);  // unfilled | partial (touched CE) | filled
}
```

**CE (Consequent Encroachment)** = midpoint of FVG. Prefer reaction at CE.

**Already in code:** `IctEngine.detectFvgs` — enhance with CE field + fill-state machine if not fully exposed on `IctSnapshot.Zone`.

### 2.2 Order Block Detection

```java
List<OrderBlock> deriveOrderBlocks(List<OhlcBar> bars, StructureEvent structure, boolean displacement) {
    List<OrderBlock> obs = new ArrayList<>();
    if (!displacement || structure.event == NONE) return obs;

    int dispIdx = findDisplacementIndex(bars);
    for (int i = dispIdx - 1; i >= Math.max(0, dispIdx - 10); i--) {
        OhlcBar b = bars.get(i);
        if (structure.direction == LONG && b.close() < b.open()) {  // last down-close
            obs.add(new OrderBlock(b.low(), b.high(), "BULL", i));
            break;
        }
        if (structure.direction == SHORT && b.close() > b.open()) {  // last up-close
            obs.add(new OrderBlock(b.low(), b.high(), "BEAR", i));
            break;
        }
    }
    return obs;
}
```

**Invalidation:** Close beyond OB extreme in opposite direction.

### 2.3 Optimal Trade Entry (OTE) Fibonacci — **PRIORITY ADD**

ICT OTE zone = **61.8% – 79%** retracement of impulse swing, with **70.5% sweet spot**.

```java
public record OteZone(double deep, double sweet, double shallow, double invalidation) {}

public OteZone computeOte(double swingStart, double swingEnd, String direction) {
    double range = Math.abs(swingEnd - swingStart);
    if ("LONG".equalsIgnoreCase(direction) || "long".equals(direction)) {
        // Fib drawn from low (start) → high (end)
        double ote79  = swingEnd - 0.79  * range;   // deepest
        double ote705 = swingEnd - 0.705 * range;   // sweet spot
        double ote62  = swingEnd - 0.618 * range;   // shallow edge
        return new OteZone(ote79, ote705, ote62, swingStart);
    } else {
        // SHORT – Fib from high → low
        double ote79  = swingEnd + 0.79  * range;
        double ote705 = swingEnd + 0.705 * range;
        double ote62  = swingEnd + 0.618 * range;
        return new OteZone(ote79, ote705, ote62, swingStart);
    }
}
```

**Usage:** Prefer entry when price enters OTE **and** overlaps active OB/FVG **and** structure alignment.  
**Targets (extensions):** −0.27, −0.62 of the same Fib tool (beyond 0%).

**Integration into `IctEngine.selectEntry`:**

```text
candidates = OB ∪ FVG matching direction
score(c) = overlap(c, oteZone) ? +2 : 0
         + near(c.mid, ote.sweet) ? +1 : 0
         + fill_state == fresh/partial ? +1 : 0
pick highest score; else fall back to first matching OB then FVG (current behavior)
```

### 2.4 Liquidity Sweep + Reclaim (Enhanced)

Priority order of pools:

1. AHH / ALH  
2. PDH / PDL  
3. EQH / EQL  
4. Round numbers (multiples of 5 / 10 / 50 / 100)  
5. Session extremes  

```java
SweepEvent detectSweepReclaim(List<OhlcBar> bars, List<Pool> pools, int reclaimBars) {
    for (Pool p : prioritySort(pools)) {
        for (int i = Math.max(0, bars.size() - 20); i < bars.size(); i++) {
            if (tradesBeyond(bars.get(i), p)) {
                boolean reclaimed = true;
                for (int k = 1; k <= reclaimBars && i + k < bars.size(); k++) {
                    if (!closesInside(bars.get(i + k), p)) {
                        reclaimed = false;
                        break;
                    }
                }
                if (reclaimed) return new SweepEvent(p, true, i);
            }
        }
    }
    return SweepEvent.NONE;
}
```

**Already in code:** AHH/ALH + PDH/PDL + sweep/reclaim. Add EQH/EQL + rounds next.

### 2.5 Equal Highs / Equal Lows (EQH / EQL) — **PRIORITY ADD**

```java
List<Pool> detectEqualLevels(List<OhlcBar> swingsOrBars, double eps) {
    // Group swing highs within eps (0.3–0.8 pts; default equal_eps_pts=0.6) → EQH
    // Group swing lows within eps → EQL
    // Prefer clusters with touchCount >= 2
    // Emit Pool("EQH"|"EQL", avgPrice, side)
}
```

### 2.6 Round-Number Magnets

```java
List<Pool> roundNumberPools(double midPrice) {
    // Nearest multiples of 5, 10, 50, 100 within ±ATR*2 of midPrice
    // name: ROUND_5 | ROUND_10 | ROUND_50 | ROUND_100
}
```

### 2.7 Premium / Discount

Dealing range = recent swing low → swing high (or H1/H4 session range).  
EQ = midpoint.  
Longs preferred in Discount (below EQ), Shorts in Premium (above EQ).

### 2.8 Advanced ICT (Phase B+)

| Concept | Status | Rule sketch |
|---------|--------|-------------|
| **Breaker Block** | Missing | Failed OB: close through OB → polarity flips → opposite POI |
| **Mitigation Block** | Missing | First touch of OB that partially fills then continues |
| **IFVG** | Missing | FVG violated by body close → flips polarity |
| **Unicorn** | Missing | Breaker + FVG confluence (high-prob reversal) |
| **Inducement** | Implicit | Explicit shallow high/low before major liquidity |

**Implement order:** OTE → EQH/EQL → rounds → Breaker → IFVG.

**Structure rule for gold:** Prefer **body close** for BOS/MSS (not wick-only) — gold spikes 100–300 pts regularly.

---

## 3. Enhanced Gann Algorithms

### 3.1 Square of 9 — Exact Formula

Core transform from pivot `P`:

```text
root = Math.sqrt(P)
level(k) = Math.pow(root + k, 2)   // k ∈ ±0.125, ±0.25, ±0.5, ±1.0, ±2.0, …
```

**Recommended increments for XAUUSD (higher volatility):**

- Fine: 0.25, 0.5, 1.0  
- Coarse/strong: Odd `+2n`; Even convention **frozen**: `(2n−1)`  

```java
List<So9Level> buildSo9(double pivot, Config cfg) {
    double root = Math.sqrt(pivot);
    List<So9Level> levels = new ArrayList<>();
    for (double step : List.of(0.25, 0.5, 1.0)) {
        for (int n = 1; n <= 8; n++) {
            levels.add(new So9Level("FINE", +n * step, Math.pow(root + n * step, 2)));
            levels.add(new So9Level("FINE", -n * step, Math.pow(root - n * step, 2)));
        }
    }
    for (int n = 1; n <= 4; n++) {
        levels.add(new So9Level("ODD", +2.0 * n, Math.pow(root + 2.0 * n, 2)));
        levels.add(new So9Level("ODD", -2.0 * n, Math.pow(root - 2.0 * n, 2)));
        // EVEN convention (freeze): (2n-1)
        levels.add(new So9Level("EVEN", +(2 * n - 1), Math.pow(root + (2 * n - 1), 2)));
        levels.add(new So9Level("EVEN", -(2 * n - 1), Math.pow(root - (2 * n - 1), 2)));
    }
    return uniqueSorted(levels);
}
```

**Proximity:** `|price − level| ≤ max(0.0008 * pivot, 0.5)` (0.08% or 0.5 pts).

**Worked example (Gold ~2400):**

| k | level |
|---|-------|
| √2400 ≈ 48.9898 | — |
| +0.5 | ≈ 2449.24 |
| +1.0 | ≈ 2498.98 |
| −0.5 | ≈ 2351.26 |

### 3.2 1×1 Angle / Equilibrium / Stretch

```java
double slope = atr(bars, 14);  // points per bar on entry TF
int t = barsSince(originBar, current);
double equilibrium = pivotPrice + (directionSign * slope * t);
double deviation = currentClose - equilibrium;
double stretchAtr = (atr > 0) ? deviation / atr : 0.0;

String angleBias;
if (Math.abs(stretchAtr) < 0.75) angleBias = "BALANCED";
else if (Math.abs(stretchAtr) < 1.25) angleBias = "EXTENDED";
else angleBias = "ALERT";  // overextended → Mode R fuel
```

Fan projections: 1×1 (`slope`), 2×1 (`2*slope`), 1×2 (`0.5*slope`) forward H bars.

### 3.3 Time Squaring

```java
int mins = minutesBetween(originNy, asofNy);
double priceMove = Math.abs(current - pivot);
double expected = mins * timeScale;  // tunable 0.5–2.0, start 1.0

boolean nearTime = Math.abs(mins - milestone) <= 5;  // 45 / 90 / 180
boolean nearPrice = Math.abs(priceMove - expected) <= tol;
boolean nearSquare = nearTime || nearPrice || timePriceBalance(...);
```

Session fractions of **540 min** (08:00–17:00 NY): 1/8, 1/4, 1/3, 1/2, 3/4, 7/8.

### 3.4 Multi-Day Cycles (Positional)

Optional overlay: 3 / 7 / 14 / 21 / 30 / 60 / 90 calendar or trading-day cycles from major swing origins. Emit watch flags only until confluence promotes Mode R/C.

---

## 4. Volume & Multi-Factor Analysis

### 4.1 Volume Profile Proxy (Tick Volume)

- Average volume over last 20 bars.  
- Spike ≥ 1.5–1.8× average → confirms displacement.  
- Climax volume at swing extremes → optional exhaustion filter.

### 4.2 DXY / SMT

Inverse correlation: gold strength often when DXY weakens. `MarketDataService.bars("DXY", tf)` stores DXY OHLC; `SmtDetector` compares last two swing highs/lows on M15 (fallback H1) vs XAUUSD structure. Wired into `ConfluenceEngine` as soft score (+1 aligned, −1 divergent); missing DXY data fails soft (`NO_DXY_DATA`).

### 4.3 News Blackout

Expand `NewsCalendarService`: NFP, FOMC, CPI ±30–60 min → `NEWS_VETO`. Hard deny new entries; optionally force-protect open paper.

---

## 5. Confluence Scoring (Enhanced Precise Version)

Aligns with `docs/theory/CONFLUENCE-FRAMEWORK.md`; this section is the **computable** form.

```java
ConfluenceDecision score(IctSnapshot ict, GannSnapshot gann, Context ctx) {
    int score = 0;
    List<String> reasons = new ArrayList<>();
    String mode = "NONE";
    String direction = "FLAT";

    // Direction agreement first (see matrix in CONFLUENCE-FRAMEWORK.md §4)
    if (alignLong(ict, gann)) direction = "LONG";
    else if (alignShort(ict, gann)) direction = "SHORT";
    else if (conflict(ict, gann)) return noTrade("CONFLICT");

    // Mode R — Reversal
    if (ict.liquidity().event().equals("sweep") && ict.liquidity().reclaim()
            && "MSS".equals(ict.structure().event()) && ctx.inKillzone) {
        mode = "R";
        score += 2; reasons.add("SWEEP_RECLAIM");
        score += 2; reasons.add("MSS_ALIGNED");
        if (Math.abs(gann.angle().stretchAtr()) >= 1.25) { score += 2; reasons.add("STRETCH_ALERT"); }
        if (gann.so9().atLevel()) { score += 1; reasons.add("SO9"); }
        if (gann.timeSquare().anyNearSquare()) { score += 1; reasons.add("NEAR_SQUARE"); }
        if (pdAligned(ict, direction)) { score += 1; reasons.add("PD_ALIGN"); }
    }
    // Mode C — Continuation: BOS + pullback to OB/FVG + hold 1×1
    // Mode T — near_square watch only

    if (ctx.inKillzone) { score += 1; reasons.add("KZ_" + ctx.kz); }
    if (ctx.volumeSpike) { score += 1; reasons.add("VOL_SPIKE"); }

    String grade;
    if (score >= 7 && aligned) grade = "A+";
    else if (score >= 5) grade = "A";
    else if (score >= 3) grade = "B";
    else grade = "C";

    // Stop = beyond sweep extreme + buffer (0.5–1.0 * ATR)
    // T1 = EQ or 1×1 (nearer first); T2 = opposite liquidity / next So9
    // If ICT stop and Gann stop disagree → use WIDER stop for paper safety

    return new ConfluenceDecision(...);
}
```

### Grades → Automation

| Grade | Score | Policy |
|-------|-------|--------|
| A+ | ≥7 + ALIGN | Paper auto-confirm eligible (flag-gated) |
| A | 5–6 + ALIGN | Operator confirm |
| B | 3–4 | Alert only |
| C | 1–2 | Journal |
| F | conflict / veto / midday new-entry | NO_TRADE |

---

## 6. Risk & Position Sizing

### 6.1 Fixed Fractional (current RiskGate pattern)

```java
double positionSize(double equity, double riskPct, double entry, double stop) {
    double riskAmount = equity * (riskPct / 100.0);  // e.g. 0.5
    double stopDistance = Math.abs(entry - stop);
    if (stopDistance <= 0) return 0;
    // XAUUSD: contract size depends on broker; treat units as "lots" abstractly in paper
    double units = riskAmount / stopDistance;
    return Math.floor(units * 100) / 100;  // 0.01 lot steps
}
```

### 6.2 ATR Buffer

Stop = extreme ± (0.5–1.0 × ATR(14)); tighter for scalp.

### 6.3 Circuit Breakers

| Limit | Default |
|-------|---------|
| Max risk / trade | 0.5% (style overrides below) |
| Max open positions (v1) | 1 |
| Daily loss halt | −2R |
| Weekly loss halt | −5R (add) |
| Kill switch | operator / system |

### 6.4 MarketQualityGate — **ADD**

Even A+ can be vetoed:

| Check | Action |
|-------|--------|
| Spread > style threshold (e.g. 25–40 pts) | Deny |
| ATR percentile top 5% | Reduce size or require higher grade |
| Recent large gap | Extra confirmation |
| Low volume / holiday | Suppress |
| Missing `ny_time` | Fail closed |
| Duplicate signal within N minutes | Suppress |

---

## 7. Position Lifecycle Manager — **PRIORITY ADD**

Current paper path: `ALERTED → PAPER_OPEN → CLOSED` (confirm/dismiss). Needed for multi-style:

```text
ALERTED
   ↓ (confirm / auto A+)
PAPER_OPEN
   ↓
┌──────────────┬─────────────────┬──────────────────┐
│ PARTIAL      │ TRAILING        │ ADD_LEG          │ CLOSED
│ (at T1)      │ (BE + trail)    │ (pyramiding)     │ (stop/target/
│              │                 │                  │  time/flip/news)
└──────────────┴─────────────────┴──────────────────┘
```

```java
class PositionManager {

    void onBar(Position p, OhlcBar bar, GannSnapshot gann, IctSnapshot ict, StyleProfile style) {
        // +1.0R → move stop to BE (+ small buffer)
        // T1 hit → close scaleOutPct (40–50%), ensure BE
        // Trail remainder: rising 1×1 (Mode C) | last swing | ATR trail 1.0–1.5×
        // Structure flip (MSS against) → force close
        // Scalp: end of killzone → force close or tight trail
        // News veto → protect / close
        // Max hold exceeded → force close
    }

    boolean canAdd(Position open, ConfluenceDecision signal, StyleProfile style) {
        return sameDirection(open, signal)
            && (signal.grade().equals("A") || signal.grade().equals("A+"))
            && open.unrealizedR() >= 0.8
            && totalRiskAfterAdd <= style.maxRiskPct()
            && open.legs() < style.maxLegs();
    }
}
```

**Add size:** 50–70% of original (or fixed R). Max legs: scalp 1, day 2, positional 3.

---

## 8. Style Profiles — **PRIORITY ADD**

```java
enum TradingStyle { SCALP, DAY, POSITIONAL }

record StyleProfile(
    IctConfig ict,
    GannConfig gann,
    RiskConfig risk,
    int maxLegs,
    Duration maxHold,
    boolean requireKillzone,
    double beTriggerR,
    double scaleOutPct,
    double maxSpreadPts
) {}
```

| Parameter | Scalping | Day Trading | Positional |
|-----------|----------|-------------|------------|
| Entry TF | M1/M5 | M5/M15 | H1 |
| Structure TF | M5/M15 | M15/H1 | H4 |
| Bias TF | H1 | H1/H4 | D1 |
| Min FVG size | 0.4 pts | 0.8 pts | 1.5 pts |
| ATR alert (Gann) | 1.0 | 1.25 | 1.5 |
| Typical stop | 20–60 pts | 50–150 pts | 150–400 pts |
| Target RR | 1:1.5–2 | 1:2–3 | 1:3+ |
| Max risk / trade | 0.3–0.5% | 0.5–0.75% | 0.75–1.0% |
| Max open legs | 1 | 1–2 | 1–3 |
| Killzone | Strict | Preferred | Optional |
| Hold horizon | Minutes | Hours | Days |

Wire via `StyleRegistry` → `PipelineService` selects profile per run / operator setting.

---

## 9. Backtesting Algorithm — **CRITICAL MISSING**

### 9.1 Architecture

```text
Historical OHLC store
        ↓
Bar-by-bar loop (real IctEngine + GannEngine + ConfluenceEngine + RiskGate)
        ↓
Fill simulator (spread / slippage / gap)
        ↓
PositionManager (BE, partial, trail, add)
        ↓
Trade log + equity curve + metrics
        ↓
Walk-forward / Monte-Carlo / Regime analysis
```

### 9.2 Skeleton

```java
class Backtester {
    BacktestResult run(List<OhlcBar> history, StyleProfile style, RiskPolicy risk) {
        for (int i = lookback; i < history.size(); i++) {
            List<OhlcBar> window = history.subList(0, i + 1);
            IctSnapshot ict = ictEngine.compute(...);
            GannSnapshot gann = gannEngine.compute(...);
            ConfluenceDecision dec = confluence.score(ict, gann, ...);
            if (dec.automation().equals("allow") && risk.ok(dec)) {
                FillResult fill = simulateFill(dec.entry(), nextBar(history, i), spread, style);
                if (fill.filled()) openTrade(dec, fill);
            }
            manageOpenPositions(history.get(i), ...);
            updateEquityMetrics();
        }
        return exportMetrics();
    }
}
```

### 9.3 Realistic Fills (Gold)

```java
FillResult simulateFill(Entry entry, OhlcBar nextBar, double spreadPts, TradingStyle style) {
    // Reject if spread > style max
    // SCALP: require touch + close back in direction
    // DAY/POS: fill if bar trades into zone
    // Apply half-spread + random slippage 0–15 pts (tunable 10–30 pts typical)
    // Gap through zone → fill at open ± slippage or reject per policy
}
```

### 9.4 Must-Have Metrics

- Expectancy (R), Profit Factor, Max DD %, Sharpe / Recovery Factor  
- Win rate, avg win / avg loss  
- Breakdown by Mode (R/C), Grade, Killzone, Style, Session  
- MFE / MAE distributions  
- Walk-forward (e.g. 6m train → 2m test, rolling)  
- Monte-Carlo shuffle of trade sequence  
- Parameter sensitivity ±10–20%  
- Regime split (HIGH_VOL vs LOW_VOL)

**Avoid overfitting:** never promote on in-sample only.

---

## 10. Journal & Analytics Contract

Every closed trade stores:

- Full decision object (ICT + Gann reasons)  
- Style used  
- Entry quality (depth into OTE / OB / FVG)  
- MFE / MAE, time in trade  
- Exit reason: `T1` | `T2` | `STOP` | `STRUCTURE_FLIP` | `TIME` | `NEWS` | `MANUAL` | `KILLZONE_END`  
- R-multiple achieved  
- Legs / adds used  

Analytics queries: expectancy by Mode × Killzone; best hours; adds vs single-leg; So9 reaction hit-rate.

---

## 11. Safe Promotion Path

```text
1. Research engines          ← current core
2. Paper + operator confirm  ← current
3. Paper auto A+ after backtest validation
4. Micro-live (tiny size) + hard daily loss + kill switch + CSS auth
5. Gradual size increase after statistical sample
```

Hard rules never bypassed: max daily loss, max open risk, news blackout, kill switch, CSS auth for live.

---

## 12. Master Roadmap (Phased)

### Phase 0 — Foundations (largely done)

- Ingest / OHLC store / `ny_time`  
- ICT + Gann + Confluence + RiskGate + Pipeline + basic paper journal  
- Angular UI scaffold + CSS auth path  

### Phase A — Completeness (1–2 weeks)

1. `OteCalculator` + integrate into `IctEngine.selectEntry`  
2. EQH/EQL + round-number pools  
3. `StyleProfile` + `StyleRegistry` (SCALP / DAY / POSITIONAL)  
4. `MarketQualityGate` (spread / gap / regime)  

### Phase B — Position Intelligence (1–2 weeks)

5. `PositionManager` (BE, partial TP, trail, limited pyramiding)  
6. Enhanced journal: MFE/MAE, exit reason, entry quality  

### Phase C — Measurement (1–2 weeks)

7. Bar-by-bar `Backtester` with realistic fills  
8. Walk-forward + Monte-Carlo + metrics export  
9. Analytics dashboard / CSV export  

### Phase D — Hardening

10. Breaker + IFVG  
11. Multi-day Gann cycles  
12. DXY SMT (optional)  
13. Micro-live gate preparation  
14. UI annotations: OB, FVG, So9, 1×1, OTE on chart  

---

## 13. Implementation Priority Checklist (for Cursor)

| # | Item | Effort | Impact |
|---|------|--------|--------|
| 1 | Shared utilities audit (ATR, swings, volume) — reuse existing | Low | — |
| 2 | OTE calculator + entry selection | Low | High |
| 3 | EQH/EQL + round pools | Low | High |
| 4 | Style profiles + pipeline wiring | Medium | High |
| 5 | MarketQualityGate | Low | High |
| 6 | PositionManager | Medium | Very High |
| 7 | Backtester + metrics | High | Critical |
| 8 | Confluence weight tuning (walk-forward) | Medium | High |
| 9 | Breaker / IFVG | Medium | Medium |
| 10 | Chart level renderer (Angular) | Medium | Medium |

---

## 14. Open Calibration Tasks (Paper First)

- Optimal `time_scale` by ATR quartile  
- So9 step sizes that historically produced reactions on XAUUSD  
- Confluence weight optimization (walk-forward)  
- News blackout windows (NFP, FOMC, CPI ±30–60 min)  
- Slippage/spread model realistic for gold (10–30 pts typical)  
- Optimal `equal_eps_pts` by ATR regime  
- Whether Mode C requires So9 or only 1×1 hold  
- ICT EQ vs Gann 1×1 as T1 when both exist  

---

## 15. Synthetic Test Scenarios (Must Pass)

1. London Judas → NY continuation  
2. Sweep + MSS + stretch alert → Mode R A+  
3. Continuation BOS holding 1×1 → Mode C  
4. Midday chop → suppressed / grade ≤ B  
5. Conflict ICT long vs Gann fade → NO_TRADE  
6. Wick beyond PDL without reclaim → no high-quality reversal  
7. Tiny FVG < min size → ignored  
8. Missing `ny_time` → `DATA_GAP` fail closed  
9. Spread spike → MarketQualityGate deny  
10. +1R then T1 → BE + partial close behavior  

---

## 16. Cursor Prompt Pack

Copy-paste when implementing:

**OTE**

> Implement `OteCalculator` and integrate the OTE zone into `IctEngine.selectEntry`. Prefer zones that overlap OTE + OB/FVG. Add unit tests with synthetic impulse swings. Spec: `docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md` §2.3.

**Style profiles**

> Create `StyleProfile` + `StyleRegistry`. Make `PipelineService` and engines accept SCALP / DAY / POSITIONAL configs per §8 of DEEP-ALGORITHMS-AND-CALCULATIONS.md.

**Position management**

> Implement `PositionManager` with BE after +1R, partial at T1, trail remainder, limited pyramiding per style (§7).

**Backtester**

> Create a minimal `Backtester` that runs real `IctEngine`/`GannEngine`/`ConfluenceEngine`/`RiskGate` bar-by-bar, simulates gold fills with spread/slippage, and exports expectancy, PF, max DD (§9).

**EQH/EQL**

> Extend `IctEngine.buildPools` with EQH/EQL cluster detection and round-number magnets (§2.5–2.6).

---

## 17. Related Files (Cross-Links)

| Doc | Role | Update note |
|-----|------|-------------|
| [`ICT-SIGNAL-ENGINE.md`](./ICT-SIGNAL-ENGINE.md) | ICT compute SoT | Enhance with OTE + volume + EQH |
| [`GANN-CYCLE-ENGINE.md`](./GANN-CYCLE-ENGINE.md) | Gann compute SoT | Freeze So9 even convention (already specified) |
| [`../theory/CONFLUENCE-FRAMEWORK.md`](../theory/CONFLUENCE-FRAMEWORK.md) | Decision fusion | Adopt precise scoring §5 |
| [`AUTOMATION-PIPELINE.md`](./AUTOMATION-PIPELINE.md) | Signal → risk → paper | Wire RiskVerdict + PositionManager + QualityGate |
| [`../theory/ICT-GOLD.md`](../theory/ICT-GOLD.md) | ICT theory | — |
| [`../theory/GANN-INTRADAY-TIME-CYCLES.md`](../theory/GANN-INTRADAY-TIME-CYCLES.md) | Gann theory | — |

---

## 18. Architecture Snapshot

```text
Python ingest (MT5 / broker) → PostgreSQL OHLC
        ↓
Spring Boot: IctEngine ‖ GannEngine
        ↓
ConfluenceEngine → MarketQualityGate → RiskGate
        ↓
PositionManager ← PipelineService / scheduled manage job
        ↓
Paper journal + (future) execution adapter
        ↓
Angular: signals, confluence viz, chart levels, journal, backtest UI
```

**Stack:** Angular 19 standalone UI · Spring Boot Java engines · Python ingest · PostgreSQL · CSS auth for live.

---

**This document is self-contained enough to generate production-quality Java (and optional Python backtest) from existing models.**  
Next highest-leverage implementations: **OTE**, **StyleProfile**, **PositionManager**, **Backtester** — in that order unless measurement is blocking (then Backtester jumps to #1).
