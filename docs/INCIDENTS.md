# INCIDENTS — Trading Portal

Chronological trace of operational incidents: symptom → root cause → fix. Newest first.
Paper-only system; no live execution involved in any entry below.

---

## INC-2026-07-16-06 — Cursor shell cleanup killed PREPROD ingest (SEV-4)

- **Symptom:** After killing abandoned diagnostic shells, `:4342` went DOWN.
- **Root cause:** PREPROD MT5 daemon was a **child** of Cursor background shell `330706`; killing the wrapper tree also stopped the daemon.
- **Fix:** Restarted `run-ingest-preprod.ps1 -Mode mt5 --daemon --health`. Documented daemon hygiene in `docs/OPS.md` §8b — keep intentional long-runners; only kill true zombies.

---

## INC-2026-07-16-05 — Cross-env schema pollution via inherited `SPRING_DATASOURCE_*` (SEV-3)

- **Symptom:** PREPROD `/api/ops/soak` reported `soak_met=false` / 2 decisions while SQL showed 30 rows in `preprod.paper_journal` (matched **prod** counts).
- **Root cause:** Agent shell retained `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` from CSS/agent-portal launches; Spring Boot env binding overrode profile JDBC URL → wrong schema.
- **Fix:** F:/G: `start.ps1` clears those env vars and pins `--spring.datasource.url=...?currentSchema=<env>` + username/password + hibernate default_schema. Templates in `scripts/start-preprod.ps1` / `start-prod.ps1`.

---

## INC-2026-07-16-04 — Postgres connection saturation (SEV-3)

- **When:** 2026-07-16, during 0.2.0 real-MT5 ingest validation.
- **Symptom:** `FATAL: remaining connection slots are reserved for roles with the SUPERUSER attribute`. MT5 ingest `db.connect` failed twice; `:3342` health momentarily `db:false`; `psql` also refused.
- **Root cause:** Shared Postgres `:5432` has `max_connections=100` (~97 usable). ~8 low-traffic internal backends (CSS ×3 envs, agent-portal ×2, trading-portal ×3) each ran HikariCP at its **default pool of 10**, reserving ~80 idle connections baseline. No headroom for ad-hoc clients (psql, Python ingest, bursts) → saturation at 106/100.
- **Immediate mitigation:** superuser `pg_terminate_backend` on long-idle (`>60s`) `app_%` connections → down to ~26 total; health green. (Emergency only — not a steady-state fix.)
- **Permanent fix (Grok `VERDICT=PROCEED` + user GO):** (1) Cap HikariCP on trading-portal, CSS (+css-next), and agent-portal (`maximum-pool-size=5`, `minimum-idle=1`, `idle-timeout=30000`, `keepalive-time=30000`) in source + deployed start scripts. (2) Bump shared PG `max_connections` **100 → 150** (restart `postgresql-x64-18`; backup `postgresql.conf.bak-2026-07-16-pool-caps`). Rolled live 2026-07-16 ~23:10 IST. Post-roll: total conns **19**, CSS roles 1–2 each (was 10–20). Proposal closed: `E:\MyAgent\workflow\db\PROPOSAL-2026-07-16-pool-caps.md`.
- **Still open:** Grok monitoring alerts (warn≥70 / crit≥85) — ops to wire.
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
