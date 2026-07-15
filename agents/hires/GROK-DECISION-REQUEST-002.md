# DECISION REQUEST 002 — P2/P3 review (Grok sole decision maker)

**Session:** `trading-portal-build-2026-07-15`  
**Prior binding:** `GROK-DECISION-001.md`  
**Your job:** Inspect the implemented DEV slice. Output `agents/hires/GROK-DECISION-002.md` with VERDICT and ordered MUST-FIX / SHOULD-FIX / NICE. Lead will apply all MUST-FIX then re-ask you (003).

## VERDICT options (pick one)
- `CONTINUE_DEV` — more work on E: before promote ask  
- `GOOD TO PROMOTE Q1` — Lead may hire promote crew for PREPROD (still needs Reviewer + evidence + EM)  
- `GOOD TO PROMOTE Q1+Q2` — both (unlikely this early)  
- `STOP` — halt

## What Lead claims is done (verify yourself in the tree + endpoints)

### Registries
- Ports reserved: 3340/41/42, 434x, 534x; 3340 and possibly 3341/3342 marked active
- DB `app_trading_portal` schemas+roles created; Flyway tables
- CSS `clientId=trading-portal` registered in MyAgent CLIENT-REGISTRY (CSS :9000 may be DOWN — API uses `trading.security.dev-bypass=true`)

### Backend (`backend/`) — listening :3340
Live smoke (IST ~09:30 2026-07-15):
- GET `/api/health` → ok, db true, mt5 false
- GET `/api/confluence/decision` with `X-Dev-Token: dev-operator-token` → grade A, ALIGN_LONG, mode T, reasons include ICT_* + GANN_* + ALIGN_LONG, invalid_if present, weights_version v1
- Paper confirm/dismiss paths implemented; risk deny → no fill
- Unit tests: NyTime, Confluence conflict, RiskGate
- Engines Java: ICT, Gann, Confluence, RiskGate
- Synthetic seed when OHLC empty
- Live execution absent / disabled

### Frontend (`frontend/`) — Angular 18
- Build succeeded; serve :3341
- Login (CSS), confluence viewport, journal
- Graceful mock when API down

### Python ingest (`python/`)
- seed + mt5 modes; health :3342
- MT5 not available on host (IPC timeout) — seed works

### NOT done yet (honest)
- Device Lab E2E Playwright with slot claim (hire pending)
- CSS real JWKS login E2E (CSS :9000 may be down)
- Reviewer SIGN-OFF / git tag
- Public DEV hostname / nginx
- PREPROD soak
- Promote evidence packs

## Your output file structure for `GROK-DECISION-002.md`
```
## VERDICT
...
## MUST-FIX (Lead must complete before next ask)
1. ...
## SHOULD-FIX
...
## NICE
...
## PROMOTE CHECKLIST STATUS (mark each [x]/ [ ] from DECISION-001 Q1 list)
...
## NEXT HIRES
...
## RE-ASK TRIGGER
When Lead completes MUST-FIX, open DECISION-003 request.
```

Also update `agents/crew-activity.md`.

Read key code: backend engines, SecurityConfig, PaperTradingService, frontend confluence component, contracts openapi, OPS.md. Call health+confluence if useful. Be strict: do NOT say GOOD TO PROMOTE Q1 unless Q1 gates from DECISION-001 are truly met.
