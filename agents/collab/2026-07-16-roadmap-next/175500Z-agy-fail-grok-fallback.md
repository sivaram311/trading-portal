# Agy → Cursor: trading-portal post-0.2.0 roadmap

**When:** 2026-07-16T17:55Z attempt · **Status:** AGY_FAIL (meta-CLI distractor; did not read brief)  
**Fallback:** Grok `cursor-grok-4.5-high` answered the same brief (saved below).

## Agy raw
See `agy-raw-out.txt` — answered `--mode` / `--dangerously-skip-permissions` docs instead of verdict block. Retry with prior promote invoke pattern also failed the same way.

## Grok verdict (authoritative for this session)

```
VERDICT=PROCEED
NEXT_VERSION=0.3.0-SNAPSHOT
ORDERED_TRACKS=1) Ops closeout (agent-portal Hikari roll + PG conn warn≥70/crit≥85) — cursor/ops — INC leftover; shared-PG safety — live-risk=none; 2) Track E PREPROD soak to soak_met=true — ops+QA — close DECISION-001 gate waived for Q2 — live-risk=none; 3) Observability harden (soak/ops dashboards, ingest daemon health, conn/pool alerts) — backend+ops — prove fleet under real MT5 without guessing — live-risk=none; 4) Paper-path reliability (replay/soak API smoke on F:, journal continuity) — backend+QA — evidence before any engine churn — live-risk=paper-only; 5) Engine depth only if soak gaps demand it — engines — defer otherwise — live-risk=none; 6) P5 micro-live — HOLD — blocked until user GO + soak_met — live-risk=blocked
MUST_NOT=- No live broker orders / MT5 order_send / broker REST
- No trading.exec.live-enabled=true on any env
- No P5 order-adapter code (even “dry-run that can go live”)
- No new engines/multi-asset/copy-trade/SaaS until soak_met + monitoring green
- No password rotation or destructive PG changes without explicit user GO
P5_STATUS=HOLD
MONITORING_PRIORITY=NOW
SOAK_PRIORITY=NOW
FOLLOW_FIRST=Restart agent-portal DEV :8080 with Hikari caps, then wire Grok PG conn monitoring (warn≥70 / crit≥85).
REASON=v0.2.0 is live paper-only; the unpaid debt is observability + PREPROD soak (soak_met still false), not new product surface. Close the INC leftover and accumulate soak evidence under real MT5 before any 0.3 feature work; P5 stays HOLD until explicit user GO after soak threshold.
```
