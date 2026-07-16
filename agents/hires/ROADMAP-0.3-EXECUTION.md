# Trading Portal — Roadmap 0.3 execution plan

**Session:** `trading-portal-roadmap-0.3-2026-07-16`  
**Decision maker:** User · **Lead:** cursor  
**Confirm:** Agy failed (meta-CLI); **Grok PROCEED** — `agents/collab/2026-07-16-roadmap-next/175500Z-agy-fail-grok-fallback.md`  
**Baseline:** `v0.2.0` live E/F/G · paper-only · P5 HOLD

## Ordered tracks (Grok)

| # | Track | Owner | Why | Live-risk | Status |
|---|-------|-------|-----|-----------|--------|
| 1 | Ops closeout (AP DEV Hikari + PG monitor warn≥70/crit≥85) | cursor/ops | INC leftover | none | **done** |
| 2 | Track E PREPROD soak → `soak_met=true` | ops+QA | Close DECISION-001 gate waived for Q2 | none | **done** |
| 3 | Observability harden | backend+ops | Fleet under real MT5 | none | **done** |
| 4 | Paper-path reliability (F: replay/soak smoke) | backend+QA | Evidence before engine churn | paper | **done** |
| 5 | Engine depth | engines | Deep-algo + SMT 2026-07-17 | none | **done** — tagged `v0.3.0` paper F/G |
| 6 | P5 micro-live | — | Exact phrase: `GO micro-live P5 on DEV only` | **blocked** | HOLD |

## MUST_NOT

- No live broker orders / `order_send` / broker REST
- No `trading.exec.live-enabled=true`
- No P5 adapter code until exact unlock phrase
- No password rotation / destructive PG without user GO

## This session progress

- [x] agent-portal DEV `:8080` up with Hikari max=5 (`app_agent_portal_dev`)
- [x] `E:\MyAgent\workflow\db\scripts\check-pg-connections.ps1` + `MONITORING-PG-CONNECTIONS.md`
- [x] Scheduled task `MyAgent-PG-Connection-Check` every 5m (log: `workflow/db/logs/pg-connections.log`)
- [x] PREPROD MT5 daemon `:4342` healthy (`last_mode=mt5`)
- [x] PREPROD soak **`soak_met=true`** (journal_decision_count=30 via ops/replay over stored OHLC; distinct_session_days=6)
- [x] ROADMAP-0.2 Track E closed
- [x] **Track 3:** `GET /api/ops/status` (soak + OHLC freshness + ingest probe + live guard); `scripts/check-fleet.ps1`; PG monitor hook
- [x] **Track 4:** PREPROD paper-path smoke (decision/journal/replay) — evidence `docs/FLEET-SMOKE-0.3.md`
- [x] Hardened F:/G: `start.ps1` — clear inherited `SPRING_DATASOURCE_*`, pin schema (fix cross-env pollution)
- [x] **Track 5 + promote:** `v0.3.0` tagged; DEV E2E 9/9; F:/G: `0.3.0` jar health ok; `live-enabled=false`

## Version

**Live tag `v0.3.0`** on E/F/G (paper-only). Remaining: **P5 HOLD** until exact unlock phrase (`agents/hires/P5-UNLOCK-STATUS-2026-07-17.md`).
