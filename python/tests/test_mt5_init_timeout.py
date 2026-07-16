"""Unit tests for MT5 init timeout behavior (no real terminal required)."""
from __future__ import annotations

import json
import subprocess
import unittest
from unittest.mock import patch

from trading_portal_ingest.mt5_source import (
    Mt5Unavailable,
    check_available,
    fetch_completed_bars,
    mt5_init_timeout_seconds,
)
from trading_portal_ingest.config import Settings


class Mt5InitTimeoutTests(unittest.TestCase):
    def test_mt5_init_timeout_default(self) -> None:
        with patch.dict("os.environ", {"INGEST_MT5_INIT_TIMEOUT_SECONDS": "10"}):
            self.assertEqual(mt5_init_timeout_seconds(), 10.0)

    def test_check_available_import_error(self) -> None:
        real_import = __import__

        def fake_import(name, globals=None, locals=None, fromlist=(), level=0):
            if name == "MetaTrader5":
                raise ImportError("MetaTrader5 package not installed")
            return real_import(name, globals, locals, fromlist, level)

        with patch("builtins.__import__", side_effect=fake_import):
            ok, detail = check_available()
        self.assertFalse(ok)
        self.assertIn("not installed", detail)

    def test_check_available_subprocess_timeout(self) -> None:
        def fake_run(*_args, **_kwargs):
            raise subprocess.TimeoutExpired(cmd="probe", timeout=1)

        with patch("trading_portal_ingest.mt5_source.subprocess.run", side_effect=fake_run):
            ok, detail = check_available()
        self.assertFalse(ok)
        self.assertIn("timed out", detail)

    def test_check_available_probe_failure(self) -> None:
        payload = {"ok": False, "detail": "MetaTrader5.initialize() failed: (-10005, 'IPC timeout')"}

        class FakeProc:
            returncode = 0
            stdout = json.dumps(payload)
            stderr = ""

        with patch("trading_portal_ingest.mt5_source.subprocess.run", return_value=FakeProc()):
            ok, detail = check_available()
        self.assertFalse(ok)
        self.assertIn("IPC timeout", detail)

    def test_fetch_completed_bars_from_subprocess_payload(self) -> None:
        payload = {
            "ok": True,
            "bars": [
                {
                    "ts_utc": "2026-01-15T12:00:00+00:00",
                    "open": 2400.0,
                    "high": 2401.0,
                    "low": 2399.0,
                    "close": 2400.5,
                    "volume": 123.0,
                }
            ],
        }

        class FakeProc:
            returncode = 0
            stdout = json.dumps(payload)
            stderr = ""

        settings = Settings(env="dev", symbol="XAUUSD", timeframes=["M1"])
        with patch("trading_portal_ingest.mt5_source.subprocess.run", return_value=FakeProc()):
            bars = fetch_completed_bars(settings, "M1", 10)
        self.assertEqual(len(bars), 1)
        self.assertEqual(bars[0].source, "mt5")

    def test_fetch_subprocess_timeout_raises(self) -> None:
        def fake_run(*_args, **_kwargs):
            raise subprocess.TimeoutExpired(cmd="fetch", timeout=1)

        settings = Settings(env="dev", symbol="XAUUSD", timeframes=["M1"])
        with patch("trading_portal_ingest.mt5_source.subprocess.run", side_effect=fake_run):
            with self.assertRaises(Mt5Unavailable) as ctx:
                fetch_completed_bars(settings, "M1", 10)
        self.assertIn("timed out", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
