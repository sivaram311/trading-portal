"""Command line entry point.

    python -m trading_portal_ingest seed
    python -m trading_portal_ingest mt5
    python -m trading_portal_ingest bootstrap-db
    python -m trading_portal_ingest health-server
"""
from __future__ import annotations

import argparse
import logging
import sys
import time

from . import db
from .config import Settings, load_settings
from .mt5_source import Mt5Unavailable, check_available, run_mt5
from .seed import run_seed

logger = logging.getLogger("trading_portal_ingest")


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="trading_portal_ingest", description=__doc__)
    sub = parser.add_subparsers(dest="mode", required=True)

    seed_p = sub.add_parser("seed", help="Write synthetic OHLC bars (always available, no MT5 required)")
    seed_p.add_argument("--bars", type=int, default=None, help="Bars per timeframe (default: INGEST_SEED_BARS or 500)")
    seed_p.add_argument("--health", action="store_true", help="Also start the health HTTP endpoint and keep running")

    mt5_p = sub.add_parser("mt5", help="Pull completed bars from a running MetaTrader5 terminal")
    mt5_p.add_argument("--bars", type=int, default=500, help="Bars per timeframe to fetch (default: 500)")
    mt5_p.add_argument("--daemon", action="store_true", help="Keep polling in a loop instead of a single run")
    mt5_p.add_argument("--interval-seconds", type=int, default=60, help="Poll interval for --daemon (default: 60)")
    mt5_p.add_argument("--health", action="store_true", help="Also start the health HTTP endpoint")

    sub.add_parser("bootstrap-db", help="Create ohlc_candle (create-if-not-exists) and exit")

    health_p = sub.add_parser("health-server", help="Run only the health HTTP endpoint (no ingest)")
    health_p.add_argument("--forever", action="store_true", default=True, help=argparse.SUPPRESS)

    sub.add_parser("check-mt5", help="Probe MT5 terminal availability and exit (no DB access)")

    return parser


def _maybe_start_health(settings: Settings):
    if not settings.health_enabled:
        return None
    from .health import start_health_server

    return start_health_server(settings)


def cmd_check_mt5() -> int:
    available, detail = check_available()
    print(f"MT5 available: {available} ({detail})")
    return 0 if available else 1


def cmd_bootstrap_db(settings: Settings) -> int:
    conn = db.connect(settings)
    try:
        ok = db.bootstrap_if_needed(conn, settings)
        return 0 if ok else 1
    finally:
        conn.close()


def cmd_seed(settings: Settings, args: argparse.Namespace) -> int:
    if args.bars:
        settings = _with_seed_bars(settings, args.bars)

    conn = db.connect(settings)
    server = None
    try:
        if not db.bootstrap_if_needed(conn, settings):
            return 1
        results = run_seed(conn, settings)
        total = sum(results.values())
        logger.info("seed complete: %s (total %d rows upserted)", results, total)

        if args.health:
            from .health import STATE

            STATE.record_success("seed", results)
            server = _maybe_start_health(settings)
            logger.info("--health passed: staying alive to serve /health. Ctrl+C to stop.")
            while True:
                time.sleep(3600)
        return 0
    finally:
        if server is not None:
            server.shutdown()
        conn.close()


def cmd_mt5(settings: Settings, args: argparse.Namespace) -> int:
    conn = db.connect(settings)
    server = None
    try:
        if not db.bootstrap_if_needed(conn, settings):
            return 1

        if args.health:
            server = _maybe_start_health(settings)

        from .health import STATE

        def run_once() -> int:
            try:
                results = run_mt5(conn, settings, bars_per_timeframe=args.bars)
                STATE.record_success("mt5", results)
                logger.info("mt5 sync complete: %s", results)
                return 0
            except Mt5Unavailable as exc:
                STATE.record_error("mt5", str(exc))
                logger.error("MT5 unavailable — failing clearly: %s", exc)
                return 2

        if not args.daemon:
            return run_once()

        logger.info("mt5 daemon mode: polling every %ds. Ctrl+C to stop.", args.interval_seconds)
        while True:
            run_once()
            time.sleep(args.interval_seconds)
    finally:
        if server is not None:
            server.shutdown()
        conn.close()


def cmd_health_server(settings: Settings) -> int:
    server = _maybe_start_health(settings)
    if server is None:
        logger.error("INGEST_HEALTH_ENABLED=false — nothing to run.")
        return 1
    logger.info("Health server running standalone. Ctrl+C to stop.")
    try:
        while True:
            time.sleep(3600)
    finally:
        server.shutdown()


def _with_seed_bars(settings: Settings, bars: int) -> Settings:
    from dataclasses import replace

    return replace(settings, seed_bars=bars)


def main(argv: list[str] | None = None) -> int:
    _configure_logging()
    parser = _build_parser()
    args = parser.parse_args(argv)

    if args.mode == "check-mt5":
        return cmd_check_mt5()

    try:
        settings = load_settings()
    except Exception as exc:
        logger.error("Configuration error: %s", exc)
        return 1

    try:
        if args.mode == "seed":
            return cmd_seed(settings, args)
        if args.mode == "mt5":
            return cmd_mt5(settings, args)
        if args.mode == "bootstrap-db":
            return cmd_bootstrap_db(settings)
        if args.mode == "health-server":
            return cmd_health_server(settings)
    except KeyboardInterrupt:
        logger.info("Interrupted — shutting down.")
        return 130
    except Exception as exc:  # pragma: no cover - top-level safety net
        logger.exception("Fatal error in %s mode: %s", args.mode, exc)
        return 1

    parser.error(f"Unknown mode {args.mode!r}")
    return 2


if __name__ == "__main__":
    sys.exit(main())
