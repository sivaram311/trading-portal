# P5 Micro-live — CODED (DEV-gated)

**Status:** Implementation present · **default still fail-closed**  
**User unlock:** “want p5 coded” (2026-07-17)  
**Prerequisite:** PREPROD soak_met=true (met)

## Defaults (all envs)

| Flag | Value |
|------|-------|
| `trading.exec.live-enabled` | **false** |
| `trading.exec.broker` | **none** |
| Kill switch | **engaged** at process start |
| `allowed-profiles` | `dev` (empty on preprod/prod) |
| `mt5-allow-send` | **false** |

## Arm DEV micro-live (operator)

1. DEV profile only (`spring.profiles.active=dev`)
2. Set `trading.exec.live-enabled=true`
3. Set `trading.exec.broker=sim` (no MT5) **or** `mt5` with bridge URL + `mt5-allow-send=true`
4. `POST /api/ops/kill-switch` body `{"engaged":false}`
5. `GET /api/live/gate` → `ok=true`
6. `POST /api/live/confirm` `{ "decision_id": "…", "note": "…" }` → journal `LIVE_OPEN`

## APIs

- `GET /api/live/gate`
- `POST /api/live/confirm`
- `GET|POST /api/ops/kill-switch`
- `GET /api/ops/status` includes `kill_switch_engaged`, `live_broker`, `live_allowed_profiles`

## Brokers

- **sim** — simulated fill, no MT5 `order_send`
- **mt5** — HTTP POST to `trading.exec.mt5-bridge-url` only if `mt5-allow-send=true`

## Forbidden without further GO

- `live-enabled=true` on F: or G:
- Shipping MT5 bridge that auto-sends without allow flag
