## Thesis
Paper-only XAUUSD confluence: ICT (OTE 62–79%, OB/FVG, breaker/IFVG, mitigation blocks, EQH/EQL, London/NY killzones) stacked with Gann (1x1 stretch from swing, Square of 9 steps, time-square, multi-day observe-only). Grade A+/A only when ICT OTE+FVG/OB aligns inside Gann 1x1 or So9 level ±ATR-scaled tolerance and killzone; Mode R primary, C for So9-vs-1x1 tension, T for EQH/EQL vs Gann 1x1 T1. Styles SCALP/DAY/POSITIONAL via RiskGate+PositionManager (BE@1R, T1 partial, ATR trail, ADD_LEG max 1). Calibrate time_scale by ATR, So9 steps, WF weights, news blackout, 10–30pt gold slippage, equal_eps. API: POST /api/backtest/run?style=&maxBars=2000&walkForward=&monteCarlo= on M15 OHLC. live-enabled=false.

## Metrics
- Net expectancy (R), PF, Sharpe, maxDD, winrate by grade (A+/A/B/C/F) and mode (R/C/T/NONE)
- Killzone hit-rate + OTE/FVG mitigation success
- Gann 1x1/So9 touch-to-T1 frequency; time-square alignment %
- BE@1R capture, T1 partial fill, ATR-trail MAE/MFE
- Slippage impact 10/20/30pts; ADD_LEG contribution
- WF stability (IS/OOS decay), MC percentile equity, bars-to-target

## Sweep Matrix
| Axis | Values | Notes |
|------|--------|-------|
| style | SCALP, DAY, POSITIONAL | maxBars≤2000 M15 |
| time_scale | ATR(14)*{0.5,1,1.5} | Gann stretch |
| So9_steps | 1–4 (45°/90°) | Mode C priority |
| equal_eps | 0.1–0.3 ATR | EQH/EQL vs 1x1 |
| WF | 3–5 folds, weights 0.6/0.3/0.1 | + MC 500 |
| blackout | news±30/60m | zero trades |
| slippage | 10/20/30 pts | gold fixed |
| grade_gate | A+ only → A+/A | Mode R/C/T |
| T1_logic | ICT EQ vs Gann 1x1 | partial + trail |

Run full factorial subset then refine winners.

## Interpretation Rules
- A+/A + Mode R + killzone + OTE in FVG/OB + 1x1/So9 = core long/short; else downgrade
- Mode C: So9 step overrides 1x1 only if ATR time_scale confirms; else NONE
- Mode T: EQH/EQL confluence with Gann 1x1 sets T1; mitigation failure aborts
- Multi-day Gann observe-only: filter, never entry
- PositionManager: BE@1R mandatory, one ADD_LEG max if grade stays A, ATR trail after T1
- Reject if breaker/IFVG flips against or outside killzone
- Expectancy >0.4R and WF OOS PF>1.2 required for grade promotion

## Invalidation Criteria
- OOS maxDD >1.5× IS or PF<1.0 any style
- Slippage 30pts turns expectancy ≤0
- So9/1x1 divergence > equal_eps without Mode C resolution
- Killzone trades <40% of A+ sample or news blackout leakage
- MC 5th percentile equity <0.7× median
- EQH/EQL false break >35% without Gann time-square support
- Any live-enabled flag or >1 open position

## Report Outline
1. Executive summary (thesis + top metrics)
2. Data/engine config (M15, ICT+Gann params, RiskGate)
3. Sweep results table + heatmaps (grade/mode/style)
4. Calibration diagnostics (time_scale, So9, equal_eps, slippage)
5. WF+MC robustness
6. Failure cases + invalidation hits
7. Paper-only next iterations (no live)
8. Appendix: raw API payloads, trade lists