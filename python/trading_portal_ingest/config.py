"""Configuration for the trading-portal Python ingest worker.

All values come from environment variables so the same code runs unchanged
across DEV (E:) / PREPROD (F:) / PROD (G:) — only the environment differs
per the machine's schema-per-env + port-per-env conventions.

Secrets (DB passwords) are **never** hardcoded here — they come from
``E:\\MyAgent\\workflow\\db\\secrets\\postgres.env`` via the environment
(see scripts/run-ingest-dev.ps1) or whatever process supervisor sets them.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field

DEFAULT_SYMBOL = "XAUUSD"
ALL_TIMEFRAMES = ["M1", "M5", "M15", "H1", "H4", "D1"]
DEFAULT_TIMEFRAMES = ["M1", "M5", "M15", "H1", "H4", "D1"]

# Timeframe -> minutes-per-bar, matches docs/contracts/schemas/ohlc-bar.json enum.
TIMEFRAME_MINUTES = {
    "M1": 1,
    "M5": 5,
    "M15": 15,
    "H1": 60,
    "H4": 240,
    "D1": 1440,
}

# MetaTrader5 timeframe constants are only resolved lazily inside mt5_source.py
# so that importing config never requires the MetaTrader5 package to be present.

ENV_SCHEMA = {
    "dev": "dev",
    "preprod": "preprod",
    "prod": "prod",
}

# Ports reserved in E:\MyAgent\workflow\ports\REGISTRY.md — trading-portal / worker.
ENV_HEALTH_PORT = {
    "dev": 3342,
    "preprod": 4342,
    "prod": 5342,
}


def _env_list(name: str, default: list[str]) -> list[str]:
    raw = os.environ.get(name)
    if not raw:
        return list(default)
    return [tf.strip().upper() for tf in raw.split(",") if tf.strip()]


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class Settings:
    env: str = field(default_factory=lambda: os.environ.get("INGEST_ENV", "dev").strip().lower())
    symbol: str = field(default_factory=lambda: os.environ.get("INGEST_SYMBOL", DEFAULT_SYMBOL).strip().upper())
    timeframes: list[str] = field(default_factory=lambda: _env_list("INGEST_TIMEFRAMES", DEFAULT_TIMEFRAMES))
    seed_bars: int = field(default_factory=lambda: int(os.environ.get("INGEST_SEED_BARS", "500")))
    seed_random_state: int = field(default_factory=lambda: int(os.environ.get("INGEST_SEED_RANDOM_STATE", "42")))

    pg_host: str = field(default_factory=lambda: os.environ.get("POSTGRES_HOST", "127.0.0.1"))
    pg_port: int = field(default_factory=lambda: int(os.environ.get("POSTGRES_PORT", "5432")))
    pg_database: str = field(default_factory=lambda: os.environ.get("TRADING_PORTAL_DB", "app_trading_portal"))

    health_enabled: bool = field(default_factory=lambda: _env_bool("INGEST_HEALTH_ENABLED", True))
    health_host: str = field(default_factory=lambda: os.environ.get("INGEST_HEALTH_HOST", "127.0.0.1"))
    health_port: int = field(default_factory=lambda: int(os.environ.get("INGEST_HEALTH_PORT", "0")) or 0)

    auto_bootstrap_ddl: bool = field(default_factory=lambda: _env_bool("INGEST_AUTO_BOOTSTRAP_DDL", True))

    def __post_init__(self) -> None:
        if self.env not in ENV_SCHEMA:
            raise ValueError(f"INGEST_ENV must be one of {list(ENV_SCHEMA)}, got {self.env!r}")
        unknown = [tf for tf in self.timeframes if tf not in TIMEFRAME_MINUTES]
        if unknown:
            raise ValueError(f"Unknown timeframe(s) {unknown}; allowed: {list(TIMEFRAME_MINUTES)}")

    @property
    def schema(self) -> str:
        return ENV_SCHEMA[self.env]

    @property
    def pg_role(self) -> str:
        env_var = f"TRADING_PORTAL_ROLE_{self.env.upper()}"
        role = os.environ.get(env_var)
        if not role:
            raise RuntimeError(
                f"{env_var} not set. Source E:\\MyAgent\\workflow\\db\\secrets\\postgres.env "
                "(see scripts/run-ingest-dev.ps1)."
            )
        return role

    @property
    def pg_password(self) -> str:
        env_var = f"TRADING_PORTAL_ROLE_{self.env.upper()}_PASSWORD"
        password = os.environ.get(env_var)
        if not password:
            raise RuntimeError(
                f"{env_var} not set. Source E:\\MyAgent\\workflow\\db\\secrets\\postgres.env "
                "(see scripts/run-ingest-dev.ps1)."
            )
        return password

    @property
    def resolved_health_port(self) -> int:
        return self.health_port or ENV_HEALTH_PORT.get(self.env, 3342)

    def dsn(self) -> dict:
        return {
            "host": self.pg_host,
            "port": self.pg_port,
            "dbname": self.pg_database,
            "user": self.pg_role,
            "password": self.pg_password,
            "options": f"-c search_path={self.schema}",
        }


def load_settings() -> Settings:
    return Settings()
