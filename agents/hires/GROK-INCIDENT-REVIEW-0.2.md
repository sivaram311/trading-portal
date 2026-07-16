# Grok review — PG connection saturation incident (2026-07-16)

Model: `cursor-grok-4.5-high` · Brief: `INCIDENT-2026-07-16-pg-conns.md`

## ASSESSMENT
Root cause is idle-pool pile-up, not Postgres capacity: ~8 Hikari defaults at 10 ≈ 80 reserved idle on a 97-usable shared instance. Raising `max_connections` alone only delays the next saturation. Demand-first caps are the correct permanent fix; a modest `max_connections` bump is optional insurance for ad-hoc clients (psql, Python ingest), not the primary remedy.

## RECOMMENDATION
1. **Demand-first (do this):** Cap Hikari — `maximum-pool-size=5`, `minimum-idle=1`, `idle-timeout=30000`, `keepalive-time=30000`. Roll trading-portal (3 envs) first, then CSS (largest hog — prod/preprod ~20 idle likely multi-instance ×10), then agent-portal with owners.
2. **Raise `max_connections` 100 → 150** after/with pool caps, one planned restart. Do **not** raise without pool caps. Record in `E:\MyAgent\workflow\db\`.
3. **Defer PgBouncer** until app/instance fan-out makes per-app caps insufficient.
4. **Do not** rely on `pg_terminate_backend` as ongoing fix — emergency only.

## MONITORING
- Alert when non-superuser `pg_stat_activity` count ≥ **70** (warn) / **85** (crit) of usable slots.
- Track idle vs active by `usename` (esp. `app_css_%`, `app_trading_portal_%`); idle spike after deploy = pool misconfig.
- Log alert on `remaining connection slots are reserved for roles with the SUPERUSER attribute`.

## VERDICT
**PROCEED.**

## Cursor actions taken
- [x] trading-portal Hikari caps in `backend/.../application.properties` (all 3 envs inherit).
- [x] Cross-app proposal filed at `E:\MyAgent\workflow\db\PROPOSAL-2026-07-16-pool-caps.md` (CSS + agent-portal + max_connections; needs owner GO).
- [x] **Rolled live 2026-07-16 ~22:51 IST** — DEV :3340 restarted from source (cap via properties); PREPROD :4340 + PROD :5340 restarted via deployed `start.ps1` with Hikari override args (tagged 0.2.0 jar unchanged). All 3 health `db:true`; decision endpoint HTTP 401 (CSS auth enforced = healthy). PG total 59, trading-portal roles ≤2 each.
