# DEV smoke — Deep Algo wave (2026-07-17)

**Grok gate:** `GOOD_FOR_DEV_SMOKE` · `DEV_SMOKE_REQUIRED=yes` · `PROMOTE=no`  
**API:** `0.3.0-SNAPSHOT` jar on `:3340` (dev profile)

## Results

| Check | Result |
|-------|--------|
| `/api/health` | ok · db true · ingest true · mt5 false |
| `/api/ops/status` `live_enabled` | **false** |
| ICT pools EQH/EQL + ROUND_* | **yes** (EQH/EQL capped ≤3/side after merge) |
| Pipeline replay | works |
| Reject path journal | REJECTED rows written |
| Confirm open → manage → close | No A/A+ in ~5d M15 window at first smoke; lifecycle covered by unit + backtester; **`POST /api/paper/close`** added for operator close |
| `mvn test` | green (incl. EQH cap/merge tests) |

## Sample backtest artifact

`backend/target/backtest-sample-metrics.csv` — synthetic only; not an edge claim.

## Follow-ups closed this commit

- EQH/EQL merge + max 3 per side  
- `POST /api/paper/close` + OpenAPI  
- Docs: OPS §0 deep-algo, this smoke note, roadmap Track 5 done  

**PROMOTE:** no (Grok + CONSCIOUS). P5 HOLD.
