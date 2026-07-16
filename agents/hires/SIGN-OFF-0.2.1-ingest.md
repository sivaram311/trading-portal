# SIGN-OFF — trading-portal v0.2.1 ingest fix (pre-push)

| Field | Value |
|-------|-------|
| Session | trading-portal-mt5-ingest-2026-07-16 |
| Reviewer agent id | readonly Release/Push Reviewer (Cursor subagent) |
| Provider | cursor |
| Base HEAD SHA | `1454454e5935ef61d3bbbceb7fd30bf2ecbd2f61` |
| Review target | **Staged index** (8 files; commit not yet created) |
| Branch / tag | `main` → follow-up commit after v0.2.0 |
| When (UTC+5:30) | 2026-07-16 ~22:35 IST |

## Checklist

- [x] Docs updated same turn (CONSCIOUS #12) — `docs/OPS.md` §8a MT5 live ingest (login-based init, external `mt5.env`, column contract)
- [x] No secrets in staged diff — grep for `Sivaram`, `213878432`, literal `password=` values: **none**. Only env-var names (`INGEST_MT5_PASSWORD`), doc placeholder `<password> # never commit`, and runtime `os.environ.get(...)` reads; scripts load `E:\MyAgent\workflow\db\secrets\mt5.env` (not in git)
- [x] Paper-only untouched — staged diff does not modify `trading.exec.live-enabled`, paper journal, or execution adapters; no `order_send` / broker REST added
- [x] MT5 scope is OHLC ingest only — `mt5_isolated.py` uses `initialize` / `symbol_select` / `copy_rates_from_pos` / `shutdown` (subprocess-isolated); login kwargs from `INGEST_MT5_*` env
- [x] Column contract matches backend Flyway SoT — `V1__init.sql` `ohlc_candle`: `symbol`, `tf`, `ts`, `ny_time`, OHLCV, `broker_time`; upsert / unique on `(symbol, tf, ts)`; removed `source` / `updated_at` from ingest DDL and upsert
- [x] Fleet splits OK — N/A (ingest/python + ops doc only)
- [x] DEV E2E / tag gates — N/A for this ingest-only follow-up (no UI/API surface change; no tag in scope)
- [x] Reviewer did not push — readonly audit only

## Verdict

**GO**

### Findings

- Staged files: `mt5_isolated.py`, `db.py`, `ddl.py`, `models.py`, `run-ingest-{dev,preprod,prod}.ps1`, `docs/OPS.md`.
- Ingest DDL bootstrap retains minor non-blocking deltas vs Flyway (e.g. `volume DEFAULT 0`, index `DESC`) — acceptable when Flyway owns the live table; upsert column set and conflict key match SoT.
- `Bar.source` field remains on the dataclass but is excluded from `as_row()` — dead field only; no DB leak.

### Lead may proceed

- Local commit of staged ingest fix on `main`
- `git push` only after this GO is recorded (done here)
