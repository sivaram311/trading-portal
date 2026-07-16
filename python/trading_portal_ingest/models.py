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
        # Columns match the backend Flyway table (SoT): symbol, tf, ts, ny_time,
        # open, high, low, close, volume, broker_time. ny_time is timestamptz in
        # the backend schema, so keep it tz-aware.
        return (
            self.symbol,
            self.timeframe,
            self.ts_utc,
            self.ny_time,
            self.open,
            self.high,
            self.low,
            self.close,
            self.volume,
            self.broker_time,
        )
