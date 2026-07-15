"""Synthetic XAUUSD OHLC generator — ``seed`` mode.

Always works without MT5. Produces a plausible-looking gold random walk so
downstream engines/portal have data to develop against on any machine,
including CI or a dev box with no MetaTrader 5 terminal.

Not a market data source — never claim these bars are real prices.
"""
from __future__ import annotations

import logging
import random
from datetime import datetime, timedelta, timezone

from .config import TIMEFRAME_MINUTES, Settings
from .models import Bar
from .timeutil import utc_to_ny

logger = logging.getLogger(__name__)

BASE_PRICE = 2400.0
BASE_VOLATILITY_PTS = 1.2  # per-M1-bar stdev, scaled by sqrt(minutes) for other TFs


def _align_down(dt: datetime, minutes: int) -> datetime:
    epoch = datetime(1970, 1, 1, tzinfo=timezone.utc)
    total_minutes = int((dt - epoch).total_seconds() // 60)
    aligned_minutes = (total_minutes // minutes) * minutes
    return epoch + timedelta(minutes=aligned_minutes)


def generate_bars(settings: Settings, timeframe: str, count: int, *, seed_offset: int = 0) -> list[Bar]:
    """Generate ``count`` completed synthetic bars ending just before "now"."""
    minutes = TIMEFRAME_MINUTES[timeframe]
    now_utc = datetime.now(timezone.utc)
    last_open = _align_down(now_utc, minutes) - timedelta(minutes=minutes)  # last *completed* bar

    rng = random.Random(settings.seed_random_state + seed_offset + hash(timeframe) % 1000)
    vol = BASE_VOLATILITY_PTS * (minutes ** 0.5)

    closes: list[float] = []
    price = BASE_PRICE + rng.uniform(-20, 20)
    for _ in range(count):
        price += rng.gauss(0, vol)
        price = max(price, 100.0)
        closes.append(price)

    bars: list[Bar] = []
    for i in range(count):
        open_ts = last_open - timedelta(minutes=minutes * (count - 1 - i))
        close_price = closes[i]
        open_price = closes[i - 1] if i > 0 else close_price + rng.gauss(0, vol * 0.3)
        high_price = max(open_price, close_price) + abs(rng.gauss(0, vol * 0.4))
        low_price = min(open_price, close_price) - abs(rng.gauss(0, vol * 0.4))
        volume = abs(rng.gauss(500, 150))

        bars.append(
            Bar(
                symbol=settings.symbol,
                timeframe=timeframe,
                ts_utc=open_ts,
                ny_time=utc_to_ny(open_ts),
                open=round(open_price, 2),
                high=round(high_price, 2),
                low=round(low_price, 2),
                close=round(close_price, 2),
                volume=round(volume, 2),
                broker_time=None,
                source="seed",
            )
        )
    return bars


def run_seed(conn, settings: Settings) -> dict[str, int]:
    from . import db

    results: dict[str, int] = {}
    for tf in settings.timeframes:
        bars = generate_bars(settings, tf, settings.seed_bars, seed_offset=hash(tf) % 97)
        written = db.upsert_bars(conn, settings.schema, bars)
        results[tf] = written
        logger.info("seed: upserted %d %s %s bars into %s.ohlc_candle", written, settings.symbol, tf, settings.schema)
    return results
