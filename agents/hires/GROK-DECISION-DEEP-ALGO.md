# GROK DECISION — Deep Algorithms Wave

**When:** 2026-07-17  
**Session:** `trading-portal-deep-algo-2026-07-17`  
**Brief:** `GROK-DECISION-REQUEST-DEEP-ALGO.md`  
**Raw:** `agents/collab/2026-07-17-deep-algo/GROK-DEEP-ALGO-raw.txt`  
**Model:** grok-4.5 (Grok CLI)

```
VERDICT=PROCEED
REASON=Tracks 1-4 closed and soak_met=true; user GO supersedes ROADMAP Track 5 "defer until soak gaps". Specs are implementation-ready (OTE/EQH/Style/Quality/PM/Backtester formulas). Unlock as 0.3.0-SNAPSHOT DEV paper-only engine depth. Minor plan revise: do not true-parallel H5/H6 with H1-H4 (H5 needs StyleProfile; H6 needs PositionManager for honest lifecycle metrics). H1/H2 both touch IctEngine — method partitions OK but expect one merge owner on that file.
UNLOCK_TRACK5=yes
WAVE_A_ORDER=H1,H2,H3,H4 > H5 > H6
DEFER=Breaker/IFVG/Unicorn/Mitigation; DXY SMT; multi-day Gann cycles; Angular OB/FVG/So9/OTE overlays (H8 optional only after green engines — default skip); walk-forward/Monte-Carlo/param-sensitivity (ship bar-by-bar metrics first); weekly loss halt optional; live path / P5 / multi-asset / SaaS
MAX_LEGS_PAPER=1
BACKTESTER=after_pm
MUST_NOT=live broker orders|order_send|trading.exec.live-enabled=true|P5 micro-live adapter|rewrite IctEngine/GannEngine/ConfluenceEngine cores|promote F:/G: this wave|schema drops/deletes without user confirm|new IdP (CSS only)|multi-symbol beyond XAUUSD|RiskGate maxLegs>1 on paper|pyramiding/ADD_LEG on paper path this wave (backtester may simulate style maxLegs offline only)|bypass news/midday/grade floor for convenience
SUCCESS_BEFORE_REASK=mvn test green after H7 integrate; unit tests per hire (OTE selectEntry prefers OTE∩OB/FVG; EQH/EQL+ROUND_* pools; StyleRegistry SCALP/DAY/POSITIONAL; QualityGate spread/gap/regime/dup veto; PositionManager BE@1R+partial T1+trail without multi-leg paper; Backtester exports expectancy_R/PF/maxDD on sample OHLC); pipeline smoke DEV paper path (decision→risk→journal open/manage/close with exit reason + MFE/MAE fields); one sample backtest run artifact (CSV or log metrics) under DEV; no live flags flipped
FOLLOW_FIRST=Start Wave A parallel H1+H2+H3+H4 immediately (H1 owns selectEntry/OteCalculator; H2 owns buildPools/equal+rounds only; H7 reconciles IctSnapshot fields). Land H3 before H5. Land H5 before H6. H7 wires style→engines→confluence→quality→risk→PM. H8 docs only unless engines+tests green and Lead has spare capacity.
```

## Lead execution order

1. **Now (parallel):** H1 OTE · H2 Liquidity · H3 Style · H4 QualityGate  
2. **Then:** H5 PositionManager (after H3)  
3. **Then:** H6 Backtester (after H5)  
4. **Then:** H7 Integrate + `mvn test`  
5. **Re-ask Grok** when SUCCESS_BEFORE_REASK met  
6. **Skip** H8 Angular overlays by default  
