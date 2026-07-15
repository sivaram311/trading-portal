"""MetaTrader 5 XAUUSD OHLC source — ``mt5`` mode.

Pulls completed bars from a **running, logged-in** MT5 terminal via the
``MetaTrader5`` Python package. Fails clearly (raises ``Mt5Unavailable``)
when the package or the terminal isn't available — per hire brief:
"pull from MetaTrader5 if terminal available; else fail clearly". The
ingest worker never fabricates data to paper over a missing terminal —
matches the "ingest fails closed" RISK in `agents/hires/GROK-DECISION-001.md`.

Pattern references (ideas/formulas only, not a fork):
``E:\\Source\\grok_dev\\python\\mt5_xauusd\\mt5_client.py``.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone

from .config import Settings
from .models import Bar
from .timeutil import utc_to_ny

logger = logging.getLogger(__name__)

# MT5 python API returns empty arrays when copy_rates_from count is too large
# (observed upstream: 100000 -> 0 bars). Mirrors grok_dev's mt5_client.py lesson.
MAX_MT5_COPY_COUNT = 10000

COMMON_MT5_PATHS = [
    r"C:\Program Files\MetaTrader 5\terminal64.exe",
    r"C:\Program Files (x86)\MetaTrader 5\terminal64.exe",
    r"D:\MT5\terminal64.exe",
    r"E:\MT5\terminal64.exe",
]

_TIMEFRAME_NAME_TO_MT5: dict[str, int] | None = None


class Mt5Unavailable(RuntimeError):
    """Raised whenever the mt5 mode cannot proceed — package missing, terminal
    not running/logged in, or symbol not selectable. Callers must treat this
    as a hard failure, not fall back to synthetic data."""


def _resolve_timeframe_map():
    global _TIMEFRAME_NAME_TO_MT5
    if _TIMEFRAME_NAME_TO_MT5 is None:
        import MetaTrader5 as mt5  # noqa: local import — see module docstring

        _TIMEFRAME_NAME_TO_MT5 = {
            "M1": mt5.TIMEFRAME_M1,
            "M5": mt5.TIMEFRAME_M5,
            "M15": mt5.TIMEFRAME_M15,
            "H1": mt5.TIMEFRAME_H1,
            "H4": mt5.TIMEFRAME_H4,
            "D1": mt5.TIMEFRAME_D1,
        }
    return _TIMEFRAME_NAME_TO_MT5


def check_available() -> tuple[bool, str]:
    """Best-effort probe: is the MetaTrader5 package importable and does a
    terminal respond? Returns ``(available, detail)`` — never raises, so the
    health endpoint can report status without crashing the process.
    """
    try:
        import MetaTrader5 as mt5
    except ImportError as exc:
        return False, f"MetaTrader5 package not installed: {exc}"

    try:
        ok = mt5.initialize()
    except Exception as exc:  # pragma: no cover - defensive
        return False, f"MetaTrader5.initialize() raised: {exc}"

    if not ok:
        err = mt5.last_error()
        return False, f"MetaTrader5.initialize() failed: {err}"

    try:
        mt5.shutdown()
    except Exception:
        pass
    return True, "MT5 terminal reachable"


def _ensure_initialized(path: str | None = None) -> None:
    import MetaTrader5 as mt5

    kwargs = {"path": path} if path else {}
    if not mt5.initialize(**kwargs):
        err = mt5.last_error()
        raise Mt5Unavailable(
            f"Failed to initialize MetaTrader5 terminal (last_error={err}). "
            "Ensure MT5 is installed, running, and logged in; "
            "'Allow DLL imports' enabled in Tools -> Options -> Expert Advisors."
        )


def fetch_completed_bars(settings: Settings, timeframe: str, count: int) -> list[Bar]:
    """Fetch up to ``count`` most recent **completed** bars from MT5.

    Raises ``Mt5Unavailable`` on any failure — package missing, terminal not
    reachable, symbol not selectable, or empty response.
    """
    try:
        import MetaTrader5 as mt5
    except ImportError as exc:
        raise Mt5Unavailable(
            "MetaTrader5 package is not installed in this environment. "
            "Install with `pip install MetaTrader5` (Windows only) or use --mode seed."
        ) from exc

    _ensure_initialized()
    try:
        if not mt5.symbol_select(settings.symbol, True):
            raise Mt5Unavailable(f"MT5 could not select symbol {settings.symbol!r} (check Market Watch).")

        tf_map = _resolve_timeframe_map()
        mt5_tf = tf_map[timeframe]
        n = min(count, MAX_MT5_COPY_COUNT)
        # position 0 = most recent (currently forming) bar; fetch n+1 and drop it.
        rates = mt5.copy_rates_from_pos(settings.symbol, mt5_tf, 0, n + 1)
        if rates is None or len(rates) == 0:
            raise Mt5Unavailable(
                f"MT5 returned no rates for {settings.symbol} {timeframe} (last_error={mt5.last_error()})."
            )

        bars: list[Bar] = []
        # Drop the last element (index -1 by MT5 ordering is oldest->newest here,
        # so the *last* row is the currently-forming bar) — never store it.
        for row in rates[:-1] if len(rates) > 1 else []:
            ts_utc = datetime.fromtimestamp(int(row["time"]), tz=timezone.utc)
            bars.append(
                Bar(
                    symbol=settings.symbol,
                    timeframe=timeframe,
                    ts_utc=ts_utc,
                    ny_time=utc_to_ny(ts_utc),
                    open=float(row["open"]),
                    high=float(row["high"]),
                    low=float(row["low"]),
                    close=float(row["close"]),
                    volume=float(row["tick_volume"]),
                    broker_time=None,
                    source="mt5",
                )
            )
        return bars
    finally:
        try:
            mt5.shutdown()
        except Exception:
            pass


def run_mt5(conn, settings: Settings, bars_per_timeframe: int = 500) -> dict[str, int]:
    """Fail-fast mt5 ingest run: fetch + upsert per configured timeframe.

    Any timeframe failure raises immediately (fail closed) rather than
    silently skipping — the caller (cli.py) surfaces this as a clear error.
    """
    from . import db

    available, detail = check_available()
    if not available:
        raise Mt5Unavailable(f"MT5 terminal not available: {detail}")

    results: dict[str, int] = {}
    for tf in settings.timeframes:
        bars = fetch_completed_bars(settings, tf, bars_per_timeframe)
        written = db.upsert_bars(conn, settings.schema, bars)
        results[tf] = written
        logger.info("mt5: upserted %d %s %s bars into %s.ohlc_candle", written, settings.symbol, tf, settings.schema)
    return results
