# INCIDENTS — Trading Portal

Chronological trace of operational incidents: symptom → root cause → fix. Newest first.
Paper-only system; no live execution involved in any entry below.

---

## INC-2026-07-16-04 — Postgres connection saturation (SEV-3)

- **When:** 2026-07-16, during 0.2.0 real-MT5 ingest validation.
- **Symptom:** `FATAL: remaining connection slots are reserved for roles with the SUPERUSER attribute`. MT5 ingest `db.connect` failed twice; `:3342` health momentarily `db:false`; `psql` also refused.
- **Root cause:** Shared Postgres `:5432` has `max_connections=100` (~97 usable). ~8 low-traffic internal backends (CSS ×3 envs, agent-portal ×2, trading-portal ×3) each ran HikariCP at its **default pool of 10**, reserving ~80 idle connections baseline. No headroom for ad-hoc clients (psql, Python ingest, bursts) → saturation at 106/100.
- **Immediate mitigation:** superuser `pg_terminate_backend` on long-idle (`>60s`) `app_%` connections → down to ~26 total; health green. (Emergency only — not a steady-state fix.)
- **Permanent fix (Grok `VERDICT=PROCEED`):** cap HikariCP on trading-portal (`maximum-pool-size=5`, `minimum-idle=1`, `idle-timeout=30000`, `keepalive-time=30000`) in `backend/.../application.properties` (all 3 envs inherit) **and** as override args in deployed `F:`/`G:` `start.ps1`. Rolled live 2026-07-16 ~22:51 IST across dev/preprod/prod (tagged 0.2.0 jar byte-unchanged). All health `db:true`; PG total fell to ~59; trading-portal roles ≤2 each.
- **Still open (owner GO):** CSS + agent-portal same caps, then raise `max_connections` 100→150. Proposal: `E:\MyAgent\workflow\db\PROPOSAL-2026-07-16-pool-caps.md`.
- **Refs:** `agents/hires/INCIDENT-2026-07-16-pg-conns.md`, `agents/hires/GROK-INCIDENT-REVIEW-0.2.md`.
- **Monitoring added (recommended):** warn ≥70 / crit ≥85 non-superuser conns; track idle-vs-active per `usename`; alert on the "reserved for SUPERUSER" FATAL string.

## INC-2026-07-16-03 — Stale DEV ingest daemon served old health (SEV-4)

- **Symptom:** after starting the real-MT5 daemon, `GET :3342/health` still reported seed-mode data from Jul 15.
- **Root cause:** two old seed-mode `python` processes (pids 13636, 15568) were still listening on `:3342`; the new daemon could not bind/serve health.
- **Fix:** terminated the stale processes; health then reported the live daemon (`status:ok`, `last_mode:mt5`, all timeframes).

## INC-2026-07-16-02 — Ingest ↔ Flyway schema mismatch (SEV-3)

- **Symptom:** Python ingest failed with `UndefinedColumn: column "timeframe" of relation "ohlc_candle" does not exist`.
- **Root cause:** backend Flyway `ohlc_candle` uses columns `symbol, tf, ts, ny_time, open, high, low, close, volume, broker_time` with unique `(symbol, tf, ts)`. The ingest worker expected legacy `timeframe`/`ts_utc` and inserted non-existent `source`/`updated_at`.
- **Fix:** aligned Python `db.py` (`_UPSERT_COLUMNS`, `ON CONFLICT`), `models.py` (`Bar.as_row()`), and `ddl.py` bootstrap to the authoritative Flyway schema. Backend is source of truth for the column contract.

## INC-2026-07-16-01 — MT5 `initialize()` IPC hang (SEV-3)

- **Symptom:** `check-mt5` / probe hung indefinitely (10s subprocess timeout); ingest could stall.
- **Root cause:** the running MT5 terminal was `NT AUTHORITY\SYSTEM` in **Session 0**, while Python ran in an interactive session → cross-session MT5 IPC never completes. Bare `mt5.initialize()` (attach mode) blocks.
- **Fix:** (1) run `mt5.initialize()` in a **subprocess with a hard timeout** so the worker never hangs (`mt5_isolated.py`); (2) use **login-based** init (`login`/`password`/`server`/`path`) so the package launches its own in-session terminal instead of attaching to the Session-0 one. Credentials in non-git `E:\MyAgent\workflow\db\secrets\mt5.env`.
