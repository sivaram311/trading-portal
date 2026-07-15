# trading-portal — Python ingest worker

XAUUSD OHLC ingest for Trading Portal. Writes candles into
`app_trading_portal.<env>.ohlc_candle` (Postgres, schema-per-env). Owned by
the **python-ingest** hire — see `agents/hires/GROK-DECISION-001.md` and
`agents/pre-work/02-architecture.md` §4/§6/§7.

Runs as a **separate deployable** from the Spring Boot backend — not a
Spring module, just another writer of the same DB.

---

## Modes

| Mode | Requires MT5? | What it does |
|------|----------------|--------------|
| `seed` | No | Writes synthetic (fake) OHLCV bars for each configured timeframe. Always works — use for local dev, CI, or any box without a live MT5 terminal. |
| `mt5` | Yes | Pulls completed bars from a **running, logged-in** MetaTrader 5 terminal via the `MetaTrader5` package. **Fails clearly** (non-zero exit, `Mt5Unavailable` error) if the package or terminal isn't reachable — it never falls back to synthetic data. |
| `bootstrap-db` | No | Creates `ohlc_candle` (create-if-not-exists) and exits. Safe to run any time — no-ops if the table already exists (e.g. created by backend Flyway). |
| `health-server` | No | Runs only the `GET /health` HTTP endpoint (no ingest). |
| `check-mt5` | No | Probes MT5 availability and exits (no DB connection needed). Useful to answer "is MT5 available on this box?" without touching data. |

Both `seed` and `mt5` upsert on `(symbol, timeframe, ts_utc)` — safe to
re-run, and safe alongside anything the Spring backend seeds/writes to the
same table (**never inserts duplicate rows for the same key**).

---

## Quick start (DEV)

```powershell
cd E:\MyWorkspace\trading-portal\python
.\scripts\run-ingest-dev.ps1                       # seed mode (default)
.\scripts\run-ingest-dev.ps1 -Mode bootstrap-db     # create table only
.\scripts\run-ingest-dev.ps1 -Mode check-mt5        # probe MT5, no DB
.\scripts\run-ingest-dev.ps1 -Mode mt5              # one-shot MT5 pull (fails clearly if no terminal)
.\scripts\run-ingest-dev.ps1 -Mode mt5 -ExtraArgs '--daemon'
.\scripts\run-ingest-dev.ps1 -Mode seed -ExtraArgs '--health'   # also serve :3342/health
```

The script creates `.venv` on first run, loads DB credentials from
`E:\MyAgent\workflow\db\secrets\postgres.env`, sets `INGEST_ENV=dev`, and
invokes `python -m trading_portal_ingest <mode>`.

### Manual invocation (any env)

```powershell
cd E:\MyWorkspace\trading-portal\python
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt

$env:INGEST_ENV = 'dev'
$env:TRADING_PORTAL_DB = 'app_trading_portal'
$env:TRADING_PORTAL_ROLE_DEV = 'app_trading_portal_dev'
$env:TRADING_PORTAL_ROLE_DEV_PASSWORD = '<see postgres.env>'
$env:POSTGRES_HOST = '127.0.0.1'
$env:POSTGRES_PORT = '5432'

.\.venv\Scripts\python.exe -m trading_portal_ingest seed
.\.venv\Scripts\python.exe -m trading_portal_ingest mt5 --daemon --interval-seconds 60
```

For PREPROD/PROD, set `INGEST_ENV=preprod|prod` and the matching
`TRADING_PORTAL_ROLE_<ENV>` / `TRADING_PORTAL_ROLE_<ENV>_PASSWORD` (roles
already reserved per `agents/pre-work/02-architecture.md` §4). Do **not**
run against F:/G: except from the correct env root per machine rules.

---

## Verified run (this hire, 2026-07-15, DEV)

- **MT5 terminal availability on this box: NOT available.** `MetaTrader5`
  Python package (`5.0.5735`) is installed, but `mt5.initialize()` returns
  `(-10005, 'IPC timeout')` — no MT5 terminal process running/reachable.
  `mt5` mode correctly fails closed with a clear error and exit code `2`;
  it never writes fake rows under the `mt5` label.
- `seed` mode: verified inserting 500 bars each for `M1/M5/M15/H1` into
  `dev.ohlc_candle` (2000 rows), idempotent on re-run (upsert, no
  duplicates for the same key), and 100% populated `ny_time`.
- `bootstrap-db` mode: verified creating `dev.ohlc_candle` when absent
  (backend Flyway migration for this table does not exist yet — see
  "Coordinating with the backend Flyway migration" below).
- Health endpoint: verified `GET http://127.0.0.1:3342/health` returns
  `{"status":"ok", "db": {"ok": true, ...}, "ingest": {...}}` while a
  `seed --health` run is active.

Run again any time with `.\scripts\run-ingest-dev.ps1 -Mode check-mt5` to
re-probe MT5 on this or another box (e.g. once a terminal is installed and
logged in).

---

## Configuration (environment variables)

| Variable | Default | Notes |
|----------|---------|-------|
| `INGEST_ENV` | `dev` | `dev` \| `preprod` \| `prod` — picks the Postgres schema + role env var names. E: boxes must use `dev` only. |
| `INGEST_SYMBOL` | `XAUUSD` | Only XAUUSD is in MVP scope (`GROK-DECISION-001`). |
| `INGEST_TIMEFRAMES` | `M1,M5,M15,H1` | Comma list; allowed: `M1,M5,M15,H1,H4,D1`. |
| `INGEST_SEED_BARS` | `500` | Bars per timeframe for `seed` mode. |
| `INGEST_SEED_RANDOM_STATE` | `42` | Seed for the synthetic random walk (reproducible runs). |
| `INGEST_HEALTH_ENABLED` | `true` | Set `false` to disable the health server entirely. |
| `INGEST_HEALTH_HOST` | `127.0.0.1` | Bind host for the health endpoint. |
| `INGEST_HEALTH_PORT` | *(env default)* | DEV `3342` / PREPROD `4342` / PROD `5342` per `workflow/ports/REGISTRY.md`. Override only for local testing. |
| `INGEST_AUTO_BOOTSTRAP_DDL` | `true` | If `false`, `seed`/`mt5` refuse to write when the table doesn't exist yet (instead of auto-creating it). |
| `POSTGRES_HOST` / `POSTGRES_PORT` | `127.0.0.1` / `5432` | Shared Postgres instance. |
| `TRADING_PORTAL_DB` | `app_trading_portal` | Database name. |
| `TRADING_PORTAL_ROLE_<ENV>` / `TRADING_PORTAL_ROLE_<ENV>_PASSWORD` | — | From `E:\MyAgent\workflow\db\secrets\postgres.env`. Never hardcode these; `run-ingest-dev.ps1` sources the secrets file for you. |

---

## Data model

### `ohlc_candle` columns

| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigserial` | PK. |
| `symbol` | `varchar(20)` | `XAUUSD` only in MVP. |
| `timeframe` | `varchar(5)` | `M1\|M5\|M15\|H1\|H4\|D1`. |
| `ts_utc` | `timestamptz` | Bar open time, UTC — absolute instant. |
| `ny_time` | `timestamp` (no tz) | Same bar open time, **America/New_York wall-clock**, DST-aware. Intentionally *not* `timestamptz` — see `trading_portal_ingest/ddl.py` docstring for why (a tz-aware column would collapse to the same instant as `ts_utc` and lose the local-hour information). |
| `open/high/low/close` | `double precision` | |
| `volume` | `double precision` | Tick volume for `mt5`; synthetic for `seed`. |
| `broker_time` | `timestamptz`, nullable | Raw broker-zone timestamp, audit only — never used for engine logic (matches `ohlc-bar.json`). Currently always `NULL` (both sources normalize on ingest). |
| `source` | `varchar(16)` | `seed` \| `mt5` — lets consumers tell synthetic dev data apart from a real feed. |
| `created_at`/`updated_at` | `timestamptz` | Bookkeeping. |

Unique constraint / upsert key: **`(symbol, timeframe, ts_utc)`**.

### Coordinating with the backend Flyway migration

At the time of this hire, `../backend` (Spring Boot) has **not been
scaffolded yet** — there is no Flyway migration to match against. Per the
hire brief, this package defines its own **create-if-not-exists** DDL
(`trading_portal_ingest/ddl.py`) so it is independently runnable now.

When the **backend** hire lands its Flyway migration for `ohlc_candle`:

1. If the backend's column names/types match this module's (`symbol`,
   `timeframe`, `ts_utc`, `ny_time`, `open/high/low/close`, `volume`,
   `broker_time`), nothing changes — Flyway's migration wins on any fresh
   DB (it should run first in a clean env), and this module's
   `CREATE TABLE IF NOT EXISTS` becomes a permanent no-op.
2. If column names differ, update `trading_portal_ingest/ddl.py` /
   `models.py` / `db.py` to match the Flyway-authoritative schema, and
   retire (or keep only as a documented fallback) the bootstrap DDL here.
3. Either way, the wire-contract JSON field names (`tf`, `ts`) in
   `docs/contracts/schemas/ohlc-bar.json` describe the **API/JSON shape**
   the Spring backend will expose — they are not required DB column names.

---

## MT5 mode details

- Uses the official `MetaTrader5` Python package (Windows only).
- Auto-initializes the default/installed terminal (no path configured by
  default); set `MT5_PATH` env var support can be added if a non-standard
  install path is needed (not required on this box — see "Verified run").
- Always drops the currently-forming bar — only **completed** candles are
  written (mirrors the lesson in
  `E:\Source\grok_dev\python\mt5_xauusd\mt5_client.py`).
- `--daemon --interval-seconds N` polls in a loop; each iteration failure
  is logged and retried on the next tick rather than crashing the daemon.
- **Never fabricates bars when MT5 is unavailable.** `Mt5Unavailable` is
  raised and surfaces as a non-zero exit / logged error — the health
  endpoint's `ingest.last_error` field reflects this too.

---

## Health endpoint

`GET http://127.0.0.1:3342/health` (DEV port, stdlib `http.server`, no
Flask dependency) returns:

```json
{
  "status": "ok",
  "app": "trading-portal-python-ingest",
  "env": "dev",
  "symbol": "XAUUSD",
  "timeframes": ["M1", "M5", "M15", "H1"],
  "db": {"ok": true, "detail": "ok"},
  "ingest": {"last_mode": "seed", "last_run_at": "...", "last_result": {"M1": 500}, "last_error": null},
  "checked_at": "..."
}
```

`status` is `degraded` (HTTP 503) if the DB connection check fails. It is
only started when `--health` is passed to `seed`/`mt5`, or via the
standalone `health-server` mode; it does not start automatically on a bare
`bootstrap-db`/`check-mt5` run.

Port `3342` is DEV-only; PREPROD/PROD use `4342`/`5342` respectively — see
`E:\MyAgent\workflow\ports\REGISTRY.md`.

---

## Project layout

```text
python/
  trading_portal_ingest/
    __init__.py
    __main__.py       # `python -m trading_portal_ingest ...`
    cli.py             # argparse entry point / subcommands
    config.py          # env-driven Settings
    models.py          # Bar dataclass
    ddl.py              # bootstrap create-if-not-exists DDL
    db.py               # connection + upsert helpers
    seed.py             # synthetic bar generator
    mt5_source.py       # MetaTrader5 client wrapper (fails clearly)
    timeutil.py         # UTC <-> America/New_York (DST-aware)
    health.py           # stdlib http.server /health endpoint
  scripts/
    run-ingest-dev.ps1  # DEV convenience runner
  requirements.txt
  README.md
```

## Dependencies

See `requirements.txt`: `psycopg2-binary`, `python-dotenv`, `tzdata`
(Windows ships no IANA tz database — required for `zoneinfo` to resolve
`America/New_York`). `MetaTrader5` is only required for `mt5` mode; install
separately with `pip install MetaTrader5` (Windows only) if not already
present.

## Non-goals (this hire)

- No live/micro-live execution — this worker only ever writes OHLC bars.
- No REST API — the Spring backend reads directly from Postgres.
- No engine logic (ICT/Gann/confluence) — that's the `engines` hire, in
  the Spring backend.
