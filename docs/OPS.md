# OPS â€” Trading Portal (DEV)

How to run and verify the DEV vertical slice (backend + engines). Paper-only; **no live execution**.

- **Decision:** `agents/hires/GROK-DECISION-001.md` Â· **Arch:** `agents/pre-work/02-architecture.md`
- **API port:** `3340` (DEV) Â· **DB:** `app_trading_portal` schema `dev` Â· **CSS clientId:** `trading-portal`

---

## 1. Prerequisites

| Tool | Version used | Notes |
|------|--------------|-------|
| JDK | 21 (Temurin) | `java -version` |
| Maven | 3.9.x | `mvn -version` |
| PostgreSQL | 18 @ `127.0.0.1:5432` | DB/schema/roles already created |
| psql (optional) | `C:\Program Files\PostgreSQL\18\bin\psql.exe` | for manual inspection |

Schema/roles are pre-provisioned (see `scripts/init-db.sql`). The DEV role + password live in
`E:\MyAgent\workflow\db\secrets\postgres.env` (`TRADING_PORTAL_ROLE_DEV[_PASSWORD]`).

## 2. Secrets (no secrets in git)

The DEV DB password is **not** committed. Provide it one of two ways:

1. **Preferred:** `scripts\run-api-dev.ps1` reads it from `E:\MyAgent\workflow\db\secrets\postgres.env`
   into a process env var (`TRADING_PORTAL_ROLE_DEV_PASSWORD`).
2. **Local file:** copy `backend/src/main/resources/application-local.properties.example`
   to `application-local.properties` (gitignored) and set the password.

`application-dev.properties` reads `${TRADING_PORTAL_ROLE_DEV_PASSWORD}` / the local file â€” it holds
**no** secret itself.

## 3. Run the API (DEV)

```powershell
powershell -File scripts\run-api-dev.ps1
```

or directly:

```powershell
cd backend
mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.profiles=dev"
```

On startup the app:
- runs Flyway migration `V1__init` into schema `dev` (6 tables),
- seeds ~500+ synthetic XAUUSD M5/M15/H1 bars **if `ohlc_candle` is empty** (`trading.seed.enabled=true`),
- computes and persists the latest ICT + Gann snapshots, the graded `ConfluenceDecision`, its
  `RiskVerdict`, and an initial journal row.

## 4. Tests

```powershell
cd backend
mvn -q test
```

Covers: `ny_time` DST transitions (spring-forward / fall-back / offsets), confluence CONFLICT â†’
`NONE`/grade `F`/`deny` and not-confirmable (no paper fill), and risk-gate denials
(confluence deny, max-open-positions, daily-loss halt). Tests use in-memory H2 (no Postgres needed).

## 5. Auth on DEV

CSS DEV :9000 JWKS required for auth (`trading.security.dev-bypass=false`).
environment (see Blockers). So DEV runs with `trading.security.dev-bypass=false (JWKS; CSS :9000 required)`:

- Public (no auth): `/api/health`, `/api/health/ny-time`, `/actuator/health`.
- All other endpoints require header **`X-Dev-Token: dev-operator-token`** (or `Authorization: Bearer dev-operator-token`).

When CSS is up, set `trading.security.dev-bypass=false` (default). The resource server then validates
CSS-issued JWTs via JWKS and requires audience/client `trading-portal`.

## 6. Smoke test (curl / PowerShell)

```powershell
# Health (public)
curl http://127.0.0.1:3340/api/health
curl http://127.0.0.1:3340/api/health/ny-time

# Protected (dev bypass header)
$h = @{ 'X-Dev-Token' = 'dev-operator-token' }
Invoke-WebRequest -UseBasicParsing -Headers $h http://127.0.0.1:3340/api/confluence/decision | % Content
Invoke-WebRequest -UseBasicParsing -Headers $h http://127.0.0.1:3340/api/engines/ict/snapshot  | % Content
Invoke-WebRequest -UseBasicParsing -Headers $h http://127.0.0.1:3340/api/engines/gann/snapshot | % Content

# Confirm a decision into a paper position (decision_id from /api/confluence/decision)
$body = @{ decision_id = '<id>'; note = 'demo' } | ConvertTo-Json
Invoke-WebRequest -UseBasicParsing -Method POST -Headers $h -ContentType 'application/json' -Body $body http://127.0.0.1:3340/api/paper/confirm

# Journal
Invoke-WebRequest -UseBasicParsing -Headers $h 'http://127.0.0.1:3340/api/paper/journal?limit=10' | % Content
```

## 7. Endpoints (contract: `docs/contracts/openapi.yaml`)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/health` | public | `ok`/`degraded`/`down`; `mt5:false` (no live feed) |
| GET | `/api/health/ny-time` | public | DST self-check; 500 on any failed case |
| GET | `/api/market/xauusd/ohlc?tf&from&to` | yes | bars ascending |
| GET | `/api/engines/ict/snapshot` | yes | latest IctSnapshot (204 if none) |
| GET | `/api/engines/gann/snapshot` | yes | latest GannSnapshot (204 if none) |
| GET | `/api/confluence/decision` | yes | latest graded decision (computes if none) |
| POST | `/api/paper/confirm` | yes | opens PAPER_OPEN (409 if CONFLICT/deny/F/already actioned) |
| POST | `/api/paper/dismiss` | yes | marks DISMISSED |
| GET | `/api/paper/journal` | yes | filter by grade/mode/direction/status/session_date/from/to |
| GET | `/api/ops/soak` | yes | PREPROD soak metrics (decision count, session days, weights) |
| GET | `/api/ops/weights` | yes | configured + distinct `weights_version` audit |
| POST | `/api/ops/replay` | yes | recompute decision at `?asof=ISO-8601` (default now) from stored OHLC |

## 8. News blackout + A+ auto-confirm

- **News veto:** `trading.news.blackouts[]` — absolute `start`/`end` instants or recurring `ny-start`/`ny-end` (+ optional `weekday`). When `asof` falls inside any window, confluence → `NEWS_VETO` / grade `F` / `deny`. Empty list = no veto.
- **A+ auto-confirm:** `trading.paper.auto-confirm-a-plus=false` (default OFF on all envs). When ON, pipeline auto-opens `PAPER_OPEN` for ALERTED A+ with risk ok and no existing open position.

## 8b. PREPROD paper soak runbook

**Gate (DECISION-001):** ≥ **30** journaled decisions **or** ≥ **10** distinct `session_date` days (whichever first).

```powershell
# After CSS login, Bearer against PREPROD API (or via staging host /api):
# GET https://trading-portal-staging.delena.buzz/api/ops/soak
# soakMet=true when targets met. Track weights via GET /api/ops/weights.
```

On PREPROD, prefer real MT5 ingest when terminal available:

```powershell
cd E:\MyWorkspace\trading-portal\python
.\scripts\run-ingest-preprod.ps1 -Mode mt5 -ExtraArgs '--daemon --health'   # :4342
```

If MT5 unavailable, seed is OK for UI/API soak **instrumentation** only — mark evidence as seed-backed, not live-OHLC soak. Replay: `POST /api/ops/replay`. P5 micro-live remains HOLD (`agents/hires/P5-MICRO-LIVE-HOLD.md`) until explicit user GO after soakMet.

### MT5 status 0.2.0

**2026-07-16 ~22:07 IST** — `E:\MyWorkspace\trading-portal\python\.venv\Scripts\python.exe -m trading_portal_ingest check-mt5`: **unavailable** (MT5 subprocess timed out after 10.0s — IPC hang; exit 1). Use seed ingest for soak instrumentation until terminal reachable; mark evidence seed-backed.

## 9. Engines (source of truth = docs)

`com.delena.tradingportal.engine.*`, pure compute over stored OHLC:
- `ict.IctEngine` â†’ `IctSnapshot` (killzone, HTF premium/discount + bias, BOS/MSS, sweep+reclaim, OB/FVG, quality 0..5)
- `gann.GannEngine` â†’ `GannSnapshot` (1x1 stretch/fan, Square-of-9, time-square milestones, cycle checkpoint, fade/trend bias)
- `confluence.ConfluenceEngine` â†’ `ConfluenceDecision` (agreement matrix, mode R/C/T/NONE, grade A+/A/B/C/F, reason codes, mandatory `invalid_if`, automation)
- `risk.RiskGate` â†’ `RiskVerdict` (confluence-deny, max risk/trade, max daily-loss halt âˆ’2R stub, max 1 open paper, news veto, grade floor)

Weights are versioned via `trading.confluence.weights-version` (default `v1`). Bump on any change.

## 10. Live execution

**Absent / hard-disabled.** No broker adapter is wired. `trading.exec.live-enabled=false` is a guard
flag only; there is no code path it enables. Live routing requires explicit **user GO**
(`agents/hires/P5-MICRO-LIVE-HOLD.md`) after PREPROD soak.

## 11. Reset seed / recompute

```sql
-- wipe DEV data (keeps schema); next startup reseeds + recomputes
truncate dev.paper_journal, dev.risk_verdict, dev.confluence_decision,
         dev.gann_snapshot, dev.ict_snapshot, dev.ohlc_candle;
```

## 12. Blockers / known gaps

- **CSS DEV :9000 JWKS required for auth (`trading.security.dev-bypass=false`).
  time). DEV therefore uses `dev-bypass=false (JWKS; CSS :9000 required)`. Real JWKS wiring is implemented and default-on when the
  flag is false; validate audience/client `trading-portal` once CSS :9000 is live.
- Synthetic seed is clearly labelled and only used when `ohlc_candle` is empty â€” the Python MT5 ingest
  worker (`:3342`) is a separate hire and not part of this backend slice.
- `flyway.baseline-on-migrate=true` + `baseline-version=0` because the `dev` schema pre-exists.

