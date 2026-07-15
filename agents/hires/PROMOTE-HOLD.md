# Promote HOLD — awaiting Grok DECISION-004

**User order:** promote to PREPROD and PROD (2026-07-15 ~10:30 IST)  
**Status:** **NO PROMOTE EXECUTED**

## Why

1. **Grok is sole decision maker** (your standing order). Promote requires exact phrases `GOOD TO PROMOTE Q1` / `GOOD TO PROMOTE Q2` in `GROK-DECISION-004.md`.
2. **All Cursor Grok models are out of usage** (`ActionRequiredError: You're out of usage`) — high/medium/low/fast variants all fail.
3. **Q2 soak not started** — DECISION-001 requires ≥30 paper decisions or ≥10 trading days on PREPROD before Q2; even with Grok online, Q2 was expected **NO-GO** until soak (unless Grok explicitly waives).

## Ready when Grok quota returns

- Exclusive Device Lab just re-proven: SoT `lastRelease.sessionId=trading-portal-build-2026-07-15` @ 10:30:53+05:30, 9/9 PASS
- Q1 evidence: `H:\releases\trading-portal-0.1.0\evidence\q1\`
- Reviewer SIGN-OFF GO
- Live DEV :3340/:3341/:3342 + CSS :9000
- Request file: `agents/hires/GROK-DECISION-REQUEST-004.md`

## Next command for Lead (after quota)

```
agent -p --trust --yolo --model cursor-grok-4.5-high --workspace E:\MyWorkspace\trading-portal
```
(prompt: execute GROK-DECISION-REQUEST-004.md)

Then on **GOOD TO PROMOTE Q1** only → hire promote-em/qa/security/review/ops/field-ops for F:.  
PROD only after **GOOD TO PROMOTE Q2**.
