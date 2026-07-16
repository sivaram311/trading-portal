"""Isolated MT5 subprocess entry points.

``MetaTrader5.initialize()`` can hang the entire Python process (including
threads) when no terminal is reachable. Parent code must invoke these helpers
via ``subprocess.run(..., timeout=...)`` so a hung IPC call only kills the
child, never the ingest worker.
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone

MAX_MT5_COPY_COUNT = 10000


def _timeframe_map():
    import MetaTrader5 as mt5

    return {
        "M1": mt5.TIMEFRAME_M1,
        "M5": mt5.TIMEFRAME_M5,
        "M15": mt5.TIMEFRAME_M15,
        "H1": mt5.TIMEFRAME_H1,
        "H4": mt5.TIMEFRAME_H4,
        "D1": mt5.TIMEFRAME_D1,
    }


def cmd_probe(path: str) -> int:
    try:
        import MetaTrader5 as mt5
    except ImportError as exc:
        json.dump({"ok": False, "detail": f"MetaTrader5 package not installed: {exc}"}, sys.stdout)
        return 0

    kwargs: dict = {}
    if path:
        kwargs["path"] = path
    ok = mt5.initialize(**kwargs)
    if ok:
        try:
            mt5.shutdown()
        except Exception:
            pass
        json.dump({"ok": True, "detail": "MT5 terminal reachable"}, sys.stdout)
        return 0

    json.dump(
        {"ok": False, "detail": f"MetaTrader5.initialize() failed: {mt5.last_error()}"},
        sys.stdout,
    )
    return 0


def cmd_fetch(symbol: str, timeframe: str, count: str, path: str) -> int:
    try:
        import MetaTrader5 as mt5
    except ImportError as exc:
        json.dump({"ok": False, "detail": f"MetaTrader5 package not installed: {exc}"}, sys.stdout)
        return 1

    kwargs: dict = {}
    if path:
        kwargs["path"] = path
    if not mt5.initialize(**kwargs):
        json.dump(
            {"ok": False, "detail": f"MetaTrader5.initialize() failed: {mt5.last_error()}"},
            sys.stdout,
        )
        return 1

    try:
        if not mt5.symbol_select(symbol, True):
            json.dump(
                {"ok": False, "detail": f"MT5 could not select symbol {symbol!r} (check Market Watch)."},
                sys.stdout,
            )
            return 1

        tf_map = _timeframe_map()
        mt5_tf = tf_map[timeframe]
        n = min(int(count), MAX_MT5_COPY_COUNT)
        rates = mt5.copy_rates_from_pos(symbol, mt5_tf, 0, n + 1)
        if rates is None or len(rates) == 0:
            json.dump(
                {
                    "ok": False,
                    "detail": f"MT5 returned no rates for {symbol} {timeframe} (last_error={mt5.last_error()}).",
                },
                sys.stdout,
            )
            return 1

        rows = []
        for row in rates[:-1] if len(rates) > 1 else []:
            ts_utc = datetime.fromtimestamp(int(row["time"]), tz=timezone.utc).isoformat()
            rows.append(
                {
                    "ts_utc": ts_utc,
                    "open": float(row["open"]),
                    "high": float(row["high"]),
                    "low": float(row["low"]),
                    "close": float(row["close"]),
                    "volume": float(row["tick_volume"]),
                }
            )
        json.dump({"ok": True, "bars": rows}, sys.stdout)
        return 0
    finally:
        try:
            mt5.shutdown()
        except Exception:
            pass


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if not args:
        print("usage: mt5_isolated probe [path] | fetch symbol timeframe count [path]", file=sys.stderr)
        return 2

    cmd = args[0]
    if cmd == "probe":
        path = args[1] if len(args) > 1 else ""
        return cmd_probe(path)
    if cmd == "fetch":
        if len(args) < 4:
            print("usage: mt5_isolated fetch symbol timeframe count [path]", file=sys.stderr)
            return 2
        path = args[4] if len(args) > 4 else ""
        return cmd_fetch(args[1], args[2], args[3], path)

    print(f"unknown command {cmd!r}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
