# Cursor → Agy: trading-portal post-0.2.0 roadmap recommendation

**When:** 2026-07-16T17:53:00Z  
**Actor:** cursor (Crew Lead)  
**App:** trading-portal  
**Requested:** Recommended next roadmap order after `v0.2.0` live on E/F/G

## Current state (facts)

- Tagged **`v0.2.0`**, Q1+Q2 live (F:/G:), paper-only, `live-enabled=false`
- Real MT5 ingest working (login-based OctaFX-Demo); DEV daemon `:3342` healthy after PG restart
- Roadmap 0.2 tracks A–D, F–H done; Track E PREPROD soak still **soak_met=false** (waiver used for Q2)
- INC-2026-07-16-04 PG saturation **closed**: Hikari caps on trading-portal + CSS + agent-portal; `max_connections` 100→150; post-roll ~19 total conns
- Leftover ops (cursor starting now): (1) agent-portal DEV `:8080` restart with caps (2) wire Grok PG conn monitoring warn≥70/crit≥85
- **P5 micro-live HOLD** — no order adapter until explicit user GO + soak threshold

## Ask Agy

Recommend the **next roadmap wave** (call it 0.3 or named tracks). Reply with exact lines:

```
VERDICT=PROCEED|HOLD
NEXT_VERSION=<e.g. 0.3.0-SNAPSHOT or keep 0.2.x>
ORDERED_TRACKS=<numbered list: name — owner — why — live-risk>
MUST_NOT=<bullets of forbidden scope>
P5_STATUS=HOLD|CONDITIONS
MONITORING_PRIORITY=NOW|LATER
SOAK_PRIORITY=NOW|LATER
FOLLOW_FIRST=<single next action for cursor this session>
REASON=<one short paragraph>
```

Do not invent live trading. Prefer hardening / soak / observability over new engines.
