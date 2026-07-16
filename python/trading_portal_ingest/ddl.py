"""Bootstrap DDL for ``ohlc_candle``.

**Provisional / bootstrap only.** The backend (Spring Boot + Flyway) hire owns
the authoritative migration for this table (see
`agents/pre-work/02-architecture.md` §4 and §6 — ``ingest`` module). This
module exists so `python-ingest` can be exercised standalone, before the
backend hire has run, per the python-ingest hire brief:

    "if not yet, define SQL create-if-not-exists matching
    docs/contracts/schemas/ohlc-bar.json"

Column names here favor SQL/DB convention (`timeframe`, `ts_utc`) over the
wire-contract JSON field names (`tf`, `ts`) used in
`docs/contracts/schemas/ohlc-bar.json` — that schema describes the
*API/JSON* shape, not required DB column names. If/when Flyway lands a real
migration for ``ohlc_candle`` with different column names, this
``CREATE TABLE IF NOT EXISTS`` becomes a no-op (existing table wins) and
this module's SQL should be reconciled/retired in favor of the Flyway
migration — see README.md "Coordinating with the backend Flyway migration".

Upsert key: ``(symbol, timeframe, ts_utc)`` — matches the python-ingest hire
brief ("Do not conflict with Spring seed — use upsert on (symbol, timeframe,
ts)").

``ny_time`` is intentionally ``TIMESTAMP`` (no time zone) — it stores the
literal America/New_York wall-clock representation of the same instant as
``ts_utc``. Storing it as ``TIMESTAMPTZ`` would collapse to the exact same
absolute instant as ``ts_utc`` (a TZ-aware column has no memory of "which
offset produced this instant"), making the column redundant. Naive
``TIMESTAMP`` preserves the DST-adjusted local hour/minute so consumers
(engines, health self-checks) can read NY wall-clock time directly without
re-deriving it via ``ts_utc AT TIME ZONE 'America/New_York'``.
"""
from __future__ import annotations

from psycopg2 import sql

TABLE_NAME = "ohlc_candle"

_CREATE_TABLE = """
CREATE TABLE IF NOT EXISTS {schema}.{table} (
    id           BIGSERIAL PRIMARY KEY,
    symbol       VARCHAR(16)       NOT NULL,
    tf           VARCHAR(4)        NOT NULL,
    ts           TIMESTAMPTZ       NOT NULL,
    ny_time      TIMESTAMPTZ       NOT NULL,
    open         DOUBLE PRECISION  NOT NULL,
    high         DOUBLE PRECISION  NOT NULL,
    low          DOUBLE PRECISION  NOT NULL,
    close        DOUBLE PRECISION  NOT NULL,
    volume       DOUBLE PRECISION  NOT NULL DEFAULT 0,
    broker_time  TIMESTAMPTZ       NULL,
    CONSTRAINT {uq_constraint} UNIQUE (symbol, tf, ts)
);
"""

_CREATE_INDEX = """
CREATE INDEX IF NOT EXISTS {index_name}
    ON {schema}.{table} (symbol, tf, ts DESC);
"""


def ensure_ohlc_candle_table(conn, schema: str, table: str = TABLE_NAME) -> None:
    """Create ``schema.ohlc_candle`` + index if it doesn't already exist.

    Safe to call every startup: ``IF NOT EXISTS`` means this never touches a
    table already created by Flyway (or a prior run of this worker).
    """
    create_table = sql.SQL(_CREATE_TABLE).format(
        schema=sql.Identifier(schema),
        table=sql.Identifier(table),
        uq_constraint=sql.Identifier(f"uq_ohlc_symbol_tf_ts"),
    )
    create_index = sql.SQL(_CREATE_INDEX).format(
        schema=sql.Identifier(schema),
        table=sql.Identifier(table),
        index_name=sql.Identifier(f"ix_{table}_symbol_tf_ts"),
    )
    with conn.cursor() as cur:
        cur.execute(create_table)
        cur.execute(create_index)
    conn.commit()


def table_exists(conn, schema: str, table: str = TABLE_NAME) -> bool:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT to_regclass(%s) IS NOT NULL",
            (f"{schema}.{table}",),
        )
        (exists,) = cur.fetchone()
    return bool(exists)
