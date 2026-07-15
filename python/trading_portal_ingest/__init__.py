"""Trading Portal — XAUUSD OHLC ingest worker.

Writes OHLC candles into ``app_trading_portal`` (schema-per-env) either from
synthetic seed data (``seed`` mode, always available) or from a running
MetaTrader 5 terminal (``mt5`` mode, fails clearly when the terminal is not
reachable). See README.md for usage.

Reference: agents/hires/GROK-DECISION-001.md, agents/pre-work/02-architecture.md,
docs/contracts/schemas/ohlc-bar.json.
"""

__version__ = "0.1.0"
