"""Postgres connection + upsert helpers for ``ohlc_candle``."""
from __future__ import annotations

import logging
from typing import Iterable

import psycopg2
from psycopg2 import sql
from psycopg2.extras import execute_values

from .config import Settings
from .ddl import TABLE_NAME, ensure_ohlc_candle_table, table_exists
from .models import Bar

logger = logging.getLogger(__name__)

_UPSERT_COLUMNS = (
    "symbol",
    "timeframe",
    "ts_utc",
    "ny_time",
    "open",
    "high",
    "low",
    "close",
    "volume",
    "broker_time",
    "source",
)


def connect(settings: Settings):
    """Open a Postgres connection using the env-role credentials.

    Raises a clear ``RuntimeError``/``psycopg2.OperationalError`` rather than
    silently degrading — ingest must fail closed on DB problems, matching the
    "never fake data" principle for the health surface.
    """
    dsn = settings.dsn()
    logger.info(
        "Connecting to postgres host=%s port=%s db=%s user=%s schema=%s",
        dsn["host"], dsn["port"], dsn["dbname"], dsn["user"], settings.schema,
    )
    return psycopg2.connect(**dsn)


def bootstrap_if_needed(conn, settings: Settings) -> bool:
    """Create ``ohlc_candle`` if it does not already exist (e.g. Flyway ran).

    Returns True if the table was (or already is) present and usable.
    """
    if table_exists(conn, settings.schema, TABLE_NAME):
        logger.info("Table %s.%s already exists (Flyway or prior run) — no bootstrap DDL executed.", settings.schema, TABLE_NAME)
        return True
    if not settings.auto_bootstrap_ddl:
        logger.error(
            "Table %s.%s does not exist and INGEST_AUTO_BOOTSTRAP_DDL=false — refusing to write.",
            settings.schema, TABLE_NAME,
        )
        return False
    logger.info("Table %s.%s not found — running bootstrap create-if-not-exists DDL.", settings.schema, TABLE_NAME)
    ensure_ohlc_candle_table(conn, settings.schema, TABLE_NAME)
    return True


def upsert_bars(conn, schema: str, bars: Iterable[Bar], table: str = TABLE_NAME) -> int:
    """Upsert bars keyed on (symbol, timeframe, ts_utc).

    Uses ``INSERT ... ON CONFLICT ... DO UPDATE`` so re-running seed/mt5 sync
    is idempotent and never duplicates rows the Spring backend (or a prior
    ingest run) already wrote for the same key — see hire brief
    "Do not conflict with Spring seed — use upsert on (symbol, timeframe, ts)".
    """
    rows = [bar.as_row() for bar in bars]
    if not rows:
        return 0

    insert_stmt = sql.SQL(
        """
        INSERT INTO {schema}.{table} ({cols})
        VALUES %s
        ON CONFLICT (symbol, timeframe, ts_utc) DO UPDATE SET
            ny_time = EXCLUDED.ny_time,
            open = EXCLUDED.open,
            high = EXCLUDED.high,
            low = EXCLUDED.low,
            close = EXCLUDED.close,
            volume = EXCLUDED.volume,
            broker_time = EXCLUDED.broker_time,
            source = EXCLUDED.source,
            updated_at = now()
        """
    ).format(
        schema=sql.Identifier(schema),
        table=sql.Identifier(table),
        cols=sql.SQL(", ").join(sql.Identifier(c) for c in _UPSERT_COLUMNS),
    )

    with conn.cursor() as cur:
        execute_values(cur, insert_stmt.as_string(cur), rows, page_size=500)
    conn.commit()
    return len(rows)


def latest_bar_ts(conn, schema: str, symbol: str, timeframe: str, table: str = TABLE_NAME):
    """Return the newest ``ts_utc`` stored for symbol/timeframe, or None."""
    query = sql.SQL(
        "SELECT max(ts_utc) FROM {schema}.{table} WHERE symbol = %s AND timeframe = %s"
    ).format(schema=sql.Identifier(schema), table=sql.Identifier(table))
    with conn.cursor() as cur:
        cur.execute(query, (symbol, timeframe))
        (ts,) = cur.fetchone()
    return ts


def count_bars(conn, schema: str, symbol: str, timeframe: str | None = None, table: str = TABLE_NAME) -> int:
    if timeframe:
        query = sql.SQL(
            "SELECT count(*) FROM {schema}.{table} WHERE symbol = %s AND timeframe = %s"
        ).format(schema=sql.Identifier(schema), table=sql.Identifier(table))
        params = (symbol, timeframe)
    else:
        query = sql.SQL(
            "SELECT count(*) FROM {schema}.{table} WHERE symbol = %s"
        ).format(schema=sql.Identifier(schema), table=sql.Identifier(table))
        params = (symbol,)
    with conn.cursor() as cur:
        cur.execute(query, params)
        (n,) = cur.fetchone()
    return n
