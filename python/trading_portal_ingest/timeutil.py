"""UTC <-> America/New_York conversion.

``ny_time`` is mandatory on every bar per
``docs/contracts/schemas/ohlc-bar.json`` and the P0 "session clock / DST"
risk in `agents/hires/GROK-DECISION-001.md`. This module is the single place
that performs the DST-aware conversion so seed/mt5 sources cannot drift.
"""
from __future__ import annotations

from datetime import datetime, timezone
from zoneinfo import ZoneInfo

NY_ZONE = ZoneInfo("America/New_York")


def to_utc(dt: datetime) -> datetime:
    """Normalize any aware/naive datetime to UTC (naive datetimes are assumed UTC)."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def utc_to_ny(dt_utc: datetime) -> datetime:
    """Convert a UTC datetime to America/New_York, DST-aware."""
    utc = to_utc(dt_utc)
    return utc.astimezone(NY_ZONE)
