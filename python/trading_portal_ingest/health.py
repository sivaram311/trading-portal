"""Optional health HTTP endpoint — stdlib ``http.server`` only (no Flask
dependency required). ``GET /health`` reports DB reachability and last
ingest run status; never fabricates a healthy response when checks fail
(per "fails closed" ingest principle, architecture doc §7).
"""
from __future__ import annotations

import json
import logging
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .config import Settings

logger = logging.getLogger(__name__)


class _HealthState:
    """Shared, thread-safe last-run status the CLI updates after each ingest."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._last_mode: str | None = None
        self._last_run_at: str | None = None
        self._last_result: dict | None = None
        self._last_error: str | None = None

    def record_success(self, mode: str, result: dict) -> None:
        with self._lock:
            self._last_mode = mode
            self._last_run_at = datetime.now(timezone.utc).isoformat()
            self._last_result = result
            self._last_error = None

    def record_error(self, mode: str, error: str) -> None:
        with self._lock:
            self._last_mode = mode
            self._last_run_at = datetime.now(timezone.utc).isoformat()
            self._last_error = error

    def snapshot(self) -> dict:
        with self._lock:
            return {
                "last_mode": self._last_mode,
                "last_run_at": self._last_run_at,
                "last_result": self._last_result,
                "last_error": self._last_error,
            }


STATE = _HealthState()


def _db_ok(settings: Settings) -> tuple[bool, str]:
    try:
        import psycopg2

        conn = psycopg2.connect(**settings.dsn(), connect_timeout=3)
        conn.close()
        return True, "ok"
    except Exception as exc:  # pragma: no cover - defensive
        return False, str(exc)


def _make_handler(settings: Settings):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):  # noqa: D401 - quiet default logging
            logger.debug("health: " + fmt, *args)

        def do_GET(self):  # noqa: N802 - required stdlib method name
            if self.path not in ("/health", "/health/"):
                self.send_response(404)
                self.end_headers()
                return

            db_ok, db_detail = _db_ok(settings)
            body = {
                "status": "ok" if db_ok else "degraded",
                "app": "trading-portal-python-ingest",
                "env": settings.env,
                "symbol": settings.symbol,
                "timeframes": settings.timeframes,
                "db": {"ok": db_ok, "detail": db_detail},
                "ingest": STATE.snapshot(),
                "checked_at": datetime.now(timezone.utc).isoformat(),
            }
            payload = json.dumps(body).encode("utf-8")
            self.send_response(200 if db_ok else 503)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    return Handler


def start_health_server(settings: Settings) -> ThreadingHTTPServer:
    """Start the health HTTP server in a background thread and return it.

    Caller is responsible for calling ``server.shutdown()`` on exit.
    """
    handler = _make_handler(settings)
    server = ThreadingHTTPServer((settings.health_host, settings.resolved_health_port), handler)
    thread = threading.Thread(target=server.serve_forever, name="health-http", daemon=True)
    thread.start()
    logger.info("Health endpoint listening on http://%s:%d/health", settings.health_host, settings.resolved_health_port)
    return server
