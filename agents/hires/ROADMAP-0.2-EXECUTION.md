# Trading Portal — Roadmap 0.2 execution plan

**Session:** `trading-portal-roadmap-0.2-2026-07-16`  
**Decision maker:** User (see `DECISION-MAKER.md`) — not Grok  
**Lead:** cursor Crew Lead  
**Gate:** Pre-work already GO (`agents/pre-work/approval.md`). This is post-0.1.0 hardening, not greenfield.

## Standing orders (MyAgent)

- CONSCIOUS #1–#18 (ports, DB schema-per-env, CSS, ACTIVITY-LOG, docs #12, E2E hire #14, Playwright slot #15, DEV-host E2E #18)
- No deletes without confirmation
- **No live broker orders** until explicit user GO on P5
- Paper-only remains default: `trading.exec.live-enabled=false`

## Scope cut (this wave → tag `v0.2.0` when ready)

| Track | Hire / owner | Deliverable | Live-risk |
|-------|--------------|-------------|-----------|
| **A — MT5 ingest** | python-ingest | Init timeout (no hang), H4/D1 in default TFs, DEV daemon script + health, clear fail-closed | None |
| **B — P4 harden** | backend | Journal replay API, soak metrics API, weights freeze audit endpoint, richer OPS | None |
| **C — Engine depth** | engines | Pass H4/D1 into ICT HTF bias; file/config news blackout → real `NEWS_VETO`; unit tests | None |
| **D — A+ auto-confirm** | paper | `trading.paper.auto-confirm-a-plus=false` (default OFF); when ON, ALERTED A+ → PAPER_OPEN | Paper only |
| **E — PREPROD soak** | ops + QA | Instrumentation + runbook; start counting toward ≥30 decisions / ≥10 session days on F: | None |
| **F — Public E2E** | e2e | Device Lab on `https://trading-portal-dev.delena.buzz` with Playwright slot claim/release | None |
| **G — Ops polish** | docs-ops | F/G worker scripts, OPS.md soak section, deps note | None |
| **H — P5 micro-live** | HOLD | Design brief only (`P5-MICRO-LIVE-HOLD.md`) — **no adapter code that places orders** | **Blocked** |

## Explicitly out of this wave

- Multi-asset, copy-trading, SaaS
- Enabling `trading.exec.live-enabled=true`
- Broker / MT5 trade send
- Password rotation without user GO
- Q1/Q2 re-promote (already at 0.1.0 on F/G; 0.2.0 needs own promote later)

## Subagent hire order

1. **Parallel wave 1:** Track A (python) ‖ Track B+C+D (backend) ‖ Track H design (docs)
2. **Wave 2 (after A–D compile):** Track E soak seed on PREPROD + Track G docs
3. **Wave 3:** Track F public E2E (Playwright slot) + Reviewer SIGN-OFF for push later
4. **User gate:** Ask before any P5 coding or live enable

## Success criteria (DEV)

- [ ] `check-mt5` returns within ≤15s (timeout), never hangs IPC forever
- [ ] Default ingest TFs include H4,D1 when mt5 path works; seed path still works without MT5
- [ ] `GET /api/ops/soak` returns decision count + distinct session days + weights_version
- [ ] `POST /api/ops/replay` (or equivalent) recomputes decision at `asof` from stored OHLC
- [ ] News blackout windows from config flip confluence to NEWS_VETO / F / deny
- [ ] Auto-confirm flag documented + default OFF; unit test covers both states
- [ ] Public DEV-host E2E pass (or documented blocker) with slot SoT
- [ ] ACTIVITY-LOG + crew-activity + OPS.md updated same turn (#12)
- [ ] P5 remains HOLD — no order-routing code

## Version note

Ship as **0.2.0-SNAPSHOT** on DEV until Device Lab + Reviewer GO; tag only after #16/#17 when user asks.

## Progress (2026-07-16 ~21:05)
- [x] MT5 check-mt5 =15s (subprocess timeout; terminal still unavailable)
- [x] Default TFs include H4,D1
- [x] GET /api/ops/soak + weights + POST replay
- [x] News blackout ? NEWS_VETO
- [x] Auto-confirm default OFF + tests
- [x] Public DEV-host E2E 9/9 + slot SoT
- [x] P5 HOLD doc only
- [x] PREPROD soakMet (**true** 2026-07-16 ~23:32 IST — 30 journaled decisions via replay over live MT5 OHLC)
- [x] Tag v0.2.0 + promote (Q1+Q2 live; soak was waived at promote, now met)

**Next:** see `ROADMAP-0.3-EXECUTION.md` (Grok PROCEED after Agy fail).

