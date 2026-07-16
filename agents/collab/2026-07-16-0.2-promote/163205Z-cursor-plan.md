# Cursor → Agy: trading-portal 0.2.0 promote plan review

**When:** 2026-07-16T16:32:05Z  
**Actor:** cursor (Crew Lead)  
**App:** trading-portal  
**Requested:** GO | HOLD on promote plan

## Proposal

1. Commit uncommitted Roadmap 0.2 work on `main` (ops soak/weights/replay, news calendar, ICT H4 HTF, MT5 subprocess timeout, A+ auto-confirm OFF, public DEV E2E).
2. Tag annotated **`v0.2.0`** after DEV E2E evidence (already 9/9 on `https://trading-portal-dev.delena.buzz` with slot SoT).
3. Reviewer SIGN-OFF before any `git push`.
4. Pack `H:\releases\trading-portal-0.2.0\` + DEPENDENCIES (CSS / css-next tags).
5. **Q1** → F: PREPROD (`:4340`/`:4341`/`:4342`, schema `preprod`, staging host).
6. **Q2** → G: PROD same day with **documented soak waiver** (soak_met still false; user asked Q1+Q2; same pattern as 0.1.0 Q2).
7. Parallel: MT5 daemon attempt, PREPROD soak accumulation, ops docs.
8. **P5 micro-live HOLD** — no broker orders.

## Context

- `v0.1.0` already live on F:/G: with Q1+Q2 GO evidence.
- Decision maker: human (not Grok). `PROMOTE-HOLD.md` is stale.
- Paper-only: `trading.exec.live-enabled=false`.

## Ask Agy (exact verdict lines required)

Reply with:

```
VERDICT=GO|HOLD
CONFIRM_PROMOTE_Q1_Q2=YES|NO
CONFIRM_SOAK_WAIVER=YES|NO
MUST_FIX=<none or bullets>
REASON=<one short paragraph>
```

If HOLD, list blockers only — do not invent new product scope.
