# Incident — Postgres connection saturation (2026-07-16)

**Severity:** SEV-3 (transient; no data loss; paper-only)  
**Detected during:** trading-portal 0.2 real MT5 ingest validation  
**Status:** mitigated (idle conns freed); permanent fix pending decision

## Symptom

- `FATAL: remaining connection slots are reserved for roles with the SUPERUSER attribute`
- Hit twice; MT5 ingest `db.connect` failed; `:3342` health momentarily `db:false`.

## Measured state

- Shared Postgres `:5432`, `max_connections=100`, `superuser_reserved_connections=3` (~97 usable).
- Total observed: **106** (over limit). Idle pools:

| Role | Idle |
|------|------|
| app_css_prod | ~20 |
| app_css_preprod | ~20 |
| app_css_dev | ~10 |
| app_agent_portal_preprod | ~10 |
| app_agent_portal_prod | ~10 |
| app_trading_portal_dev | ~10-11 |
| app_trading_portal_preprod | ~10 |
| app_trading_portal_prod | ~6-8 |

Root cause: **sum of HikariCP `maximum-pool-size` (default 10) across ~8 low-traffic backends ≈ 80 idle** baseline, leaving almost no headroom for ad-hoc clients (psql, python ingest, bursts).

## Mitigation applied

- Terminated long-idle (`>60s`) `app_%` connections via superuser `pg_terminate_backend` — Hikari reconnects on demand. Down to ~26 total; health green.

## Cursor Lead recommendation (for Grok review)

1. **Demand fix (priority):** cap HikariCP on internal apps — `maximum-pool-size=5`, `minimum-idle=1`, `idle-timeout=30000`, `keepalive-time=30000`. 8×5=40 max idle → ~57 headroom. No DB restart; rolling per app.
2. **Headroom:** bump `max_connections` 100 → 150 on shared PG (one restart; ~5-10 MB/conn). Record in `E:\MyAgent\workflow\db\`.
3. Defer PgBouncer unless app count keeps growing.
4. Start with trading-portal's own 3 backends (safe/in-scope), then CSS (biggest hog) + agent-portal with owner coordination.

## Ask Grok

- Agree with demand-first (Hikari cap) over raising `max_connections` alone?
- Right pool numbers for these low-traffic internal apps?
- Any monitoring/alert to add (e.g. pg_stat_activity threshold)?
- Verdict: PROCEED with the staged plan? (`PROCEED` / `REVISE: ...`)
