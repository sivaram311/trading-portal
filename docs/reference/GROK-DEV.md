# Reference: grok_dev

**Path:** `E:\Source\grok_dev`  
**Role:** Prior trading tooling — **reference only**. This project is a new automated trading app/portal, not a rename of grok_dev.

## Useful prior work

| Area | Where in grok_dev | Relevance |
|------|-------------------|-----------|
| Gann intraday (angles, So9, time squaring, killzones) | `docs/gann-intraday-*.md`, `frontend/docs/GANN_INTRADAY_USAGE_GUIDE.md`, `python/run_gann_intraday.py` | Seed for Gann engines |
| NY liquidity / sweep style analysis | `frontend/docs/NY_LIQUIDITY_SWEEP_ANALYZER.md`, `python/run_ny_liquidity_sweep.py` | Adjacent to ICT liquidity concepts |
| Multi-TF RSI / Gann Odd Square | Analyzer + `docs/order-rsi-mt5-alignment.md` | Research overlays — not v1 execution core |
| MT5 → Postgres XAUUSD OHLC | `python/mt5_xauusd/` | Pipeline pattern for gold data |
| Timezones / NY session | `frontend/docs/TIMEZONE_HANDLING.md`, `NY_SESSION_ONLY_FEATURE.md` | Session windows for ICT + Gann |
| Stack pattern | Spring Boot + Angular + Playwright | Possible stack candidate (subject to architecture approval) |

Also related research notes: `E:\Source\xauusd_analysis\` (OHLC TF combination notes).

## Rules for reuse

- Copy **ideas and formulas**, not blind package names / ports / schemas from grok_dev.
- New app gets its own port reservation, DB schema, CSS `clientId`, and docs.
- Prefer converging ICT + Gann into a single **confluence** decision layer rather than siloed tools alone.
