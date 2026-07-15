# ICT Theory — XAUUSD (Gold)

**Status:** Definition-grade SoT (v0 research)  
**Instrument:** XAUUSD / spot gold  
**Audience:** Quant / strategy + engine implementers  
**Disclaimer:** Research framework only. No claim of profitability. All concepts must pass paper → gated live before capital risk.

---

## 1. Purpose

Define **Inner Circle Trader (ICT)** concepts as they apply to **gold**, in language precise enough to:

1. Train operators (shared vocabulary)
2. Specify detection algorithms (`docs/algorithms/ICT-SIGNAL-ENGINE.md`)
3. Feed the confluence layer (`docs/theory/CONFLUENCE-FRAMEWORK.md`)

Reference ideas (liquidity sweeps, session windows) exist in `E:\Source\grok_dev` NY Liquidity Analyzer — this document is the **new project SoT**, not a copy.

---

## 2. Instrument quirks (gold-specific)

| Trait | Implication for ICT |
|-------|---------------------|
| 24h OTC + futures-linked liquidity | Session kills matter more than equity “open auction” alone |
| Large round-number magnets (e.g. 2300, 2350, 2400) | Treat psychologically round levels as **liquidity pools**, not magic |
| Fast displacement in London/NY | FVG / imbalance fills are common; require **displacement quality** filters |
| Overnight Asia ranges often get swept | Asian high/low are primary **liquidity** references for London/NY |
| Spreads widen off-hours | Killzone gating reduces junk signals outside active windows |

**Assumption:** Primary chart times use **America/New_York** for session logic; broker bar time is converted via configured broker zone (see grok_dev timezone lessons — reuse idea, not ports/schemas).

---

## 3. Sessions & killzones

All windows below are **New York local time** (`America/New_York`, DST-aware).

### 3.1 Macro sessions

| Session | NY window | Role on gold |
|---------|-----------|--------------|
| **Asian** | 18:00–02:00 (prev evening → early AM) | Range build; AHH/ALH liquidity |
| **London** | 03:00–08:00 | Often first expansion / manipulation |
| **New York** | 08:00–17:00 | Continuation or reversal of London narrative |
| **London–NY overlap** | 08:00–11:00 | Highest participation; preferred automation window |
| **NY afternoon** | 14:00–17:00 | Secondary expansion / profit-taking |

### 3.2 Killzones (preferred signal windows)

| Killzone | NY window | Typical ICT narrative |
|----------|-----------|------------------------|
| London Open | 03:00–05:00 | Sweep Asia → displace |
| NY Open | 08:00–10:00 | Sweep London / Asia → true move |
| NY Lunch / midday | 11:00–13:00 | Often chop — **default: no new entries** |
| NY Afternoon | 14:00–17:00 | Late continuation or mean-revert to daily open |

**Rule of thumb for v1 automation:** emit **actionable** ICT setups only when `in_killzone == true` (London Open, NY Open, or NY Afternoon). Midday = observe only.

---

## 4. Market structure

### 4.1 Swing points

- **Swing high (SH):** bar high with *N* lower highs left and right (default *N*=2 on M15; *N*=3 on H1).
- **Swing low (SL):** symmetric.

### 4.2 BOS — Break of Structure

Price **closes** beyond a swing in the **direction of the prevailing trend**.

| Trend bias | Bullish BOS | Bearish BOS |
|------------|-------------|-------------|
| Condition | Close > last SH | Close < last SL |

BOS = **continuation** confirmation, not necessarily entry.

### 4.3 MSS — Market Structure Shift

Price **closes** beyond a swing **against** the prior trend (first evidence of reversal).

| Prior bias | Bullish MSS | Bearish MSS |
|------------|-------------|-------------|
| Condition | Close > last SH after series of lower highs/lows | Close < last SL after series of higher highs/lows |

**Assumption:** MSS requires a prior clear swing sequence (≥2 swings). Isolated noise breaks are ignored.

### 4.4 Displacement

A **strong impulsive candle or run** that leaves imbalance (FVG) and breaks structure. Quality filters (v1):

- Body ≥ 60% of candle range
- Close in outer 30% of range (direction of move)
- Optional: tick volume ≥ 1.5× 20-bar average

Without displacement, treat “breaks” as weak / ignore for automation.

---

## 5. Liquidity

### 5.1 Liquidity pools (gold)

| Pool | Definition | Why it matters |
|------|------------|----------------|
| **PDH / PDL** | Prior day high / low | Classic resting stops |
| **AHH / ALH** | Asian session high / low | London/NY often raid then reverse |
| **Session high/low** | Running London or NY extreme | Trailing liquidity |
| **Equal highs / lows** | ≥2 highs/lows within *ε* (e.g. 0.3–0.8 pts on gold M15) | “EQH/EQL” — engineered stop runs |
| **Round numbers** | Multiples of 5 / 10 / 50 | Retail clustering |

### 5.2 Liquidity sweep (raid)

1. Price **trades beyond** a pool (wick or brief trade-through)
2. Then **closes back** inside the prior range / structure within *K* bars (default *K*=3 on M15)
3. Prefer sweep that **fails** to hold beyond the level (rejection)

This aligns with the grok_dev NY Liquidity Sweep narrative (sweep → structure return) but is defined here for ICT+automation without RSI as a hard requirement.

### 5.3 Inducement

A shallow high/low **before** the true liquidity pool that draws early entries. Automation should prefer sweeps of **major** pools (PDH/PDL, AHH/ALH) over first micro swing.

---

## 6. Order blocks (OB)

**Definition (computable):** The last opposing candle **before** a displacement that causes BOS/MSS.

| Bias | Order block | Invalidation |
|------|-------------|--------------|
| Bullish | Last down-close candle before bullish displacement | Close below OB low |
| Bearish | Last up-close candle before bearish displacement | Close above OB high |

**Mitigation:** Price returns into OB zone; entry on reaction (rejection wick, engulf, or FVG confluence inside OB).

**Open question:** Whether to use “breaker blocks” (failed OB flipped) in v1 — **deferred**; document only, not required for first engine.

---

## 7. Fair value gaps (FVG) / imbalance

**3-candle FVG:**

- Bullish FVG: `low[i] > high[i-2]` → gap from `high[i-2]` to `low[i]`
- Bearish FVG: `high[i] < low[i-2]` → gap from `low[i-2]` to `high[i]`

| State | Meaning |
|-------|---------|
| Unfilled | Price has not traded into the gap |
| Partially filled | Mid-gap touched |
| Filled / CE | Midpoint (consequent encroachment) or full fill |

**Gold note:** Many FVGs fill partially and continue. For entries, prefer **CE reaction** + structure alignment over “must fill completely.”

**Assumption:** Minimum FVG size = `max(0.5 × ATR(14), 0.8 points)` on entry TF to ignore noise.

---

## 8. Premium / discount (dealing range)

1. Define dealing range: recent swing low → swing high (or session range).
2. Midpoint = equilibrium (EQ).
3. **Premium** = above EQ (favor shorts for mean-revert / distribution narratives).
4. **Discount** = below EQ (favor longs for accumulation narratives).

**Rule:** In a **bullish** narrative (bullish MSS + higher timeframe draw on liquidity above), prefer **longs in discount**. Symmetric for bearish.

---

## 9. Time & price delivery narrative (ICT-style day)

Typical gold day (not guaranteed):

1. Asia builds range  
2. London raids Asia (or PDH/PDL)  
3. NY continues London or reverses after raid of London extreme  
4. Afternoon may seek daily open / opposite liquidity  

Automation should **label** which narrative is active rather than force a single pattern every day.

---

## 10. Multi-timeframe stack (recommended)

| Role | TF | Use |
|------|-----|-----|
| Bias | H1 / H4 | Dealing range, premium/discount, HTF liquidity draw |
| Structure | M15 | BOS/MSS, OB, FVG |
| Entry | M5 or M1 | Precise sweep + displacement confirmation |

**v1 default:** Bias H1, structure M15, entry M5.

---

## 11. Setup archetypes (actionable)

### A. NY liquidity raid → reversal

1. In NY Open killzone  
2. Sweep AHH or ALH (or PDH/PDL)  
3. MSS on M15 against the sweep direction  
4. Entry on FVG/OB in discount (long) or premium (short)  
5. Invalidation beyond sweep extreme; targets: EQ → opposite liquidity  

### B. London continuation

1. London Open sweeps Asia  
2. Bullish/bearish BOS with displacement  
3. Retrace to OB/FVG in discount/premium  
4. NY Open continues in same direction  

### C. Failure (trap)

1. Sweep without MSS within *K* bars → **no signal**  
2. Midday chop → suppress  
3. News spike without structure reclaim → paper-only flag  

---

## 12. Risk framing (theory → ops)

| Gate | Requirement |
|------|-------------|
| Research | Definitions stable; labeled historical examples |
| Paper | Engine emits signals with journalable R-multiples |
| Live micro | Killzone + confluence only; hard daily loss stop |
| Scale | Only after sample size + expectancy review |

**Never** treat ICT labels as predictive certainty.

---

## 13. Assumptions & open research questions

### Assumptions

- NY-time session map is primary; IST display is operator convenience only.
- Close-based BOS/MSS (not wick-only) for automation stability.
- Displacement + volume filter reduces false structure breaks.
- Round-number pools use fixed grid (5/10/50) until learned magnets exist.

### Open questions

1. Optimal *ε* for equal highs/lows on gold across regimes (trending vs compressed ATR).  
2. Whether H4 bias outperforms H1 for overnight holds (v1 is intraday-first).  
3. Interaction of ICT premium/discount with Gann 1×1 equilibrium (see confluence doc).  
4. News calendar veto rules (NFP, FOMC, CPI) — severity thresholds TBD.  
5. Breaker / mitigation block inclusion for v2.

---

## 14. Related docs

| Doc | Role |
|-----|------|
| [`GANN-INTRADAY-TIME-CYCLES.md`](./GANN-INTRADAY-TIME-CYCLES.md) | Time/geometry complementary theory |
| [`CONFLUENCE-FRAMEWORK.md`](./CONFLUENCE-FRAMEWORK.md) | Decision fusion |
| [`../algorithms/ICT-SIGNAL-ENGINE.md`](../algorithms/ICT-SIGNAL-ENGINE.md) | Computable engine |
| [`../reference/GROK-DEV.md`](../reference/GROK-DEV.md) | Prior tooling pointer |
