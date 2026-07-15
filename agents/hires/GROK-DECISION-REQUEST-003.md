# DECISION REQUEST 003 — MUST-FIX complete; ask promote readiness

**Session:** `trading-portal-build-2026-07-15`  
**Prior:** DECISION-002 = CONTINUE_DEV  
**Your role:** Sole decision maker. Write `agents/hires/GROK-DECISION-003.md`.

## Lead claims MUST-FIX done (verify)

1. **UI :3341** — listening; GET `/` → 200; port registry active  
2. **E2E Device Lab** — Playwright slot claim/release; **9/9 PASS** (phone 360×780, desktop 1280×800, tablet 800×1280); `docs/E2E.md` + `H:\releases\trading-portal-0.1.0\evidence\q1\e2e\`  
3. **CSS JWKS** — CSS `:9000` up; `trading.security.dev-bypass=false`; `POST /auth/login` clientId=trading-portal → Bearer → `/api/confluence/decision` grade A ALIGN_LONG; DataSeeder seeds trading-portal  
4. **Reviewer SIGN-OFF GO** — `H:\releases\trading-portal-0.1.0\evidence\review\SIGN-OFF.md` + `agents/hires/SIGN-OFF-0.1.0.md`  
5. **Q1 evidence pack** — `H:\releases\trading-portal-0.1.0\evidence\q1\` (health, ny-time, confluence, journal, E2E, SUMMARY)

## Live stack (re-check)
- API 3340, UI 3341, CSS 9000, worker was 3342  
- Paper-only; live exec disabled  
- Localhost-only DEV (no public hostname) — documented  
- Secrets: `application-local.properties` gitignored (contains local password); tracked props use env placeholders

## Ask
If Q1 gates from DECISION-001 are now truly met, say exactly: **GOOD TO PROMOTE Q1**  
Else CONTINUE_DEV with new MUST-FIX.  
Do **not** say GOOD TO PROMOTE Q2 yet (no PREPROD soak).

Structure:
```
## VERDICT
## MUST-FIX (if any)
## SHOULD-FIX
## PROMOTE CHECKLIST STATUS
## NEXT HIRES (promote crew only if GOOD TO PROMOTE Q1)
## RE-ASK TRIGGER
```

Update crew-activity.md.
