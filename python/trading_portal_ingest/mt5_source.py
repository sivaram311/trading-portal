"""MetaTrader 5 XAUUSD OHLC source — ``mt5`` mode.

Pulls completed bars from a **running, logged-in** MT5 terminal via the
``MetaTrader5`` Python package. Fails clearly (raises ``Mt5Unavailable``)
when the package or the terminal isn't available — per hire brief:
"pull from MetaTrader5 if terminal available; else fail clearly". The
ingest worker never fabricates data to paper over a missing terminal —
matches the "ingest fails closed" RISK in `agents/hires/GROK-DECISION-001.md`.

``initialize()`` runs in an **isolated subprocess** with a hard timeout
(default 10s via ``INGEST_MT5_INIT_TIMEOUT_SECONDS``). MT5 IPC hangs freeze
the entire Python process (even daemon threads), so subprocess isolation is
required — the parent never calls ``mt5.initialize()`` directly.

Pattern references (ideas/formulas only, not a fork):
``E:\\Source\\grok_dev\\python\\mt5_xauusd\\mt5_client.py``.
"""
from __future__ import annotations

import json
import logging
import os
import subprocess
import sys
from datetime import datetime, timezone

from .config import Settings
from .models import Bar
from .timeutil import utc_to_ny

logger = logging.getLogger(__name__)

MAX_MT5_COPY_COUNT = 10000

COMMON_MT5_PATHS = [
    r"C:\Program Files\MetaTrader 5\terminal64.exe",
    r"C:\Program Files (x86)\MetaTrader 5\terminal64.exe",
    r"D:\MT5\terminal64.exe",
    r"E:\MT5\terminal64.exe",
]


class Mt5Unavailable(RuntimeError):
    """Raised whenever the mt5 mode cannot proceed — package missing, terminal
    not running/logged in, or symbol not selectable. Callers must treat this
    as a hard failure, not fall back to synthetic data."""


def mt5_init_timeout_seconds() -> float:
    raw = os.environ.get("INGEST_MT5_INIT_TIMEOUT_SECONDS", "10").strip()
    try:
        return max(1.0, float(raw))
    except ValueError:
        return 10.0


def resolve_mt5_path() -> str | None:
    """Return explicit ``INGEST_MT5_PATH`` or the first existing common path."""
    explicit = os.environ.get("INGEST_MT5_PATH", "").strip()
    if explicit:
        return explicit
    for candidate in COMMON_MT5_PATHS:
        if os.path.isfile(candidate):
            return candidate
    return None


def _isolated_cmd(*args: str) -> list[str]:
    return [sys.executable, "-m", "trading_portal_ingest.mt5_isolated", *args]


def _run_isolated(args: list[str], *, timeout: float) -> dict:
    try:
        proc = subprocess.run(
            args,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired:
        raise Mt5Unavailable(f"MT5 subprocess timed out after {timeout}s (IPC hang)") from None

    if not proc.stdout.strip():
        detail = proc.stderr.strip() or f"MT5 subprocess exited {proc.returncode} with no output"
        raise Mt5Unavailable(detail)

    try:
        payload = json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise Mt5Unavailable(f"MT5 subprocess returned invalid JSON: {proc.stdout[:200]!r}") from exc

    return payload


def check_available() -> tuple[bool, str]:
    """Best-effort probe: is the MetaTrader5 package importable and does a
    terminal respond? Returns ``(available, detail)`` — never raises, so the
    health endpoint can report status without crashing the process.
    """
    try:
        import MetaTrader5 as mt5  # noqa: F401 - probe importability
    except ImportError as exc:
        return False, f"MetaTrader5 package not installed: {exc}"

    path = resolve_mt5_path() or ""
    timeout = mt5_init_timeout_seconds()
    try:
        payload = _run_isolated(_isolated_cmd("probe", path), timeout=timeout)
    except Mt5Unavailable as exc:
        logger.warning("%s — treating as unavailable", exc)
        return False, str(exc)

    ok = bool(payload.get("ok"))
    detail = str(payload.get("detail", "unknown"))
    return ok, detail


def fetch_completed_bars(settings: Settings, timeframe: str, count: int) -> list[Bar]:
    """Fetch up to ``count`` most recent **completed** bars from MT5.

    Raises ``Mt5Unavailable`` on any failure — package missing, terminal not
    reachable, symbol not selectable, or empty response.
    """
    try:
        import MetaTrader5 as mt5  # noqa: F401 - probe importability
    except ImportError as exc:
        raise Mt5Unavailable(
            "MetaTrader5 package is not installed in this environment. "
            "Install with `pip install MetaTrader5` (Windows only) or use --mode seed."
        ) from exc

    path = resolve_mt5_path() or ""
    timeout = mt5_init_timeout_seconds() + 30.0
    payload = _run_isolated(
        _isolated_cmd("fetch", settings.symbol, timeframe, str(count), path),
        timeout=timeout,
    )

    if not payload.get("ok"):
        raise Mt5Unavailable(str(payload.get("detail", "MT5 fetch failed")))

    bars: list[Bar] = []
    for row in payload.get("bars", []):
        ts_utc = datetime.fromisoformat(str(row["ts_utc"]))
        if ts_utc.tzinfo is None:
            ts_utc = ts_utc.replace(tzinfo=timezone.utc)
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
                volume=float(row["volume"]),
                broker_time=None,
                source="mt5",
            )
        )
    return bars


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
