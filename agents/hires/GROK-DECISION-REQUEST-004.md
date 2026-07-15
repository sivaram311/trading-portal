# DECISION REQUEST 004b — User ordered promote to PREPROD and PROD

**Session:** `trading-portal-build-2026-07-15`  
**User order (now):** “promote to preprod and prod”  
**Prior:** DECISION-003 CONTINUE_DEV; Lead completed MUST-FIX; DECISION-004 was blocked by Cursor Grok quota — retrying now.

## You are sole decision maker

Lead will **not** promote without your exact phrases:
- `GOOD TO PROMOTE Q1` → unlocks PREPROD (F:) promote crew
- `GOOD TO PROMOTE Q2` → unlocks PROD (G:) promote crew

Your DECISION-001 said Q2 needs PREPROD soak (≥30 paper decisions or ≥10 trading days). User now asked for both. Decide honestly:
- If soak unmet → may still give **GOOD TO PROMOTE Q1** only and **NO-GO Q2** with reason
- Only say **GOOD TO PROMOTE Q2** if you waive soak explicitly with risk accepted, or soak is somehow met (it is not)

## Fresh evidence (verify just before deciding)

1. Playwright exclusive re-run immediately before this ask:
   - SoT `E:\MyAgent\workflow\testing\playwright-slot.json` `lastRelease.sessionId` must be `trading-portal-build-2026-07-15`
   - Copies under `H:\releases\trading-portal-0.1.0\evidence\q1\e2e\`
2. `registry.json` `ports` 3341 = `active`
3. Live DEV: API :3340 health, UI :3341 200, CSS :9000 JWKS, `dev-bypass=false`
4. SIGN-OFF GO + Q1 pack on H:

## Output

Write `agents/hires/GROK-DECISION-004.md`:

```
## VERDICT
Q1: GOOD TO PROMOTE Q1 | CONTINUE_DEV | STOP
Q2: GOOD TO PROMOTE Q2 | NO-GO Q2 | STOP
REASON: ...

## MUST-FIX (if any before Q1)
## PREPROD DEPLOY CONSTRAINTS (if Q1 GO)
## PROD CONSTRAINTS (if Q2 GO or why NO-GO)
## NEXT HIRES
## RE-ASK TRIGGER
```

Update `agents/crew-activity.md`.
