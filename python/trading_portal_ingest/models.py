"""In-memory bar representation shared by seed + mt5 sources.

Field names mirror the DB columns (``ddl.py``), not the wire-contract JSON
names (``docs/contracts/schemas/ohlc-bar.json`` uses ``tf``/``ts``) — see
``ddl.py`` module docstring for the naming rationale.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(frozen=True)
class Bar:
    symbol: str
    timeframe: str
    ts_utc: datetime
    ny_time: datetime
    open: float
    high: float
    low: float
    close: float
    volume: float
    broker_time: Optional[datetime] = None
    source: str = "seed"

    def as_row(self) -> tuple:
        # ny_time column is naive TIMESTAMP (America/New_York wall-clock) — see
        # ddl.py docstring. Strip tzinfo explicitly so the literal NY hour/minute
        # is what lands in the DB, independent of psycopg2/libpq protocol quirks
        # around tz-aware datetimes inserted into a naive column.
        ny_time_naive = self.ny_time.replace(tzinfo=None) if self.ny_time.tzinfo else self.ny_time
        return (
            self.symbol,
            self.timeframe,
            self.ts_utc,
            ny_time_naive,
            self.open,
            self.high,
            self.low,
            self.close,
            self.volume,
            self.broker_time,
            self.source,
        )
