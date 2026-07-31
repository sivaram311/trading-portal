# AI-DLC Inception Baseline - trading-portal

**Captured:** 2026-08-01 (as-is snapshot, not a target design)

## Purpose

Trading Portal is an automated trading **application + operator portal** for **XAUUSD (gold)**. It combines ICT and W.D. Gann signal engines, confluence grading, paper trading / journal, and ops controls. Scope in-repo is paper-first (F/G paper + P5 fail-closed); live execution is coded but not armed by default.

## Tech stack

| Layer | As stated in-repo |
|-------|-------------------|
| Backend | Java **21**, Spring Boot **3.3.5** (`backend/pom.xml` parent), Maven, artifact version **0.3.2**; Spring Web, JPA, Validation, Security, OAuth2 Resource Server, Actuator; Flyway; PostgreSQL driver; H2 for tests |
| Frontend | Angular **^18.2.0**, TypeScript **~5.5.4**, Tailwind CSS **^3.4.7**, RxJS **~7.8.0**, Playwright **^1.46.0** (`frontend/package.json` version **0.3.2**) |
| Ingest worker | Python package `trading_portal_ingest`; `psycopg2-binary==2.9.12`, `python-dotenv==1.1.1`, `tzdata==2026.3`; optional `MetaTrader5` for `mt5` mode (`python/requirements.txt`, `python/README.md`) |
| Data | PostgreSQL (docs cite **18** @ `127.0.0.1:5432`), DB `app_trading_portal`, schema-per-env (`dev` / `preprod` / `prod`) |
| API contract | OpenAPI **3.1.0**, contract version **0.3.2** (`docs/contracts/openapi.yaml`) |

## Current features (as-built)

### Operator UI (Angular routes)

- `/login` — CSS password login (`clientId=trading-portal`); optional DEV token demo when configured
- `/` — Live confluence: style badge, price-rail / candle overlays, ICT/Gann tags, Confirm / Dismiss / Journal CTAs, style selector
- `/journal` — Paper journal with grade filters; exit / MFE / MAE when closed
- `/analytics` — Analytics dashboard (expectancy / session breakdown via API)

Graceful degradation: if API down, UI falls back to mock fixtures (Confirm/Dismiss not server-journaled).

### Backend API surface (see `docs/OPS.md` §7 / OpenAPI)

- Health: `GET /api/health`, `GET /api/health/ny-time`, Actuator health
- Market: `GET /api/market/xauusd/ohlc`
- Engines: `GET /api/engines/ict/snapshot`, `GET /api/engines/gann/snapshot`
- Confluence: `GET /api/confluence/decision`
- Paper: `POST /api/paper/confirm`, `POST /api/paper/dismiss`, `GET /api/paper/journal`, `POST /api/paper/close`
- Style: `GET` / `PUT /api/style` (SCALP / DAY / POSITIONAL)
- Analytics: `GET /api/analytics/summary`, `GET /api/analytics/by-session`
- Ops: `GET /api/ops/status`, `GET /api/ops/soak`, `GET /api/ops/weights`, `POST /api/ops/replay`, kill-switch
- Backtest: `GET /api/backtest/capabilities`, `POST /api/backtest/run` (optional walk-forward / Monte-Carlo)
- Live (P5 coded): `GET /api/live/gate`, `POST /api/live/confirm` — default fail-closed (`trading.exec.live-enabled=false`)

### Engines / automation (FEATURE-VALIDATION + OPS)

- ICT: OTE, liquidity EQH/EQL + rounds, breaker / IFVG / Unicorn, mitigation blocks, quality gate
- Gann: angles / So9 / cycles; multi-day cycles observe-only
- Confluence grading + risk gate (max 1 open paper position; news veto; A+ auto-confirm default OFF)
- PositionManager: BE@1R, partial T1, ATR trail, ADD_LEG (style maxLegs; never 2nd position)
- DXY SMT confirmation (`SmtDetector`; soft fail if no DXY bars)

### Python MT5 XAUUSD ingest

- Modes: `seed`, `mt5`, `bootstrap-db`, `health-server`, `check-mt5`
- One-shot by default; `--daemon` optional; health on `:3342` / `:4342` / `:5342` when `--health` or health-server

### Version / hosts (README)

- App version **0.3.2** (pom / package.json / OpenAPI / OPS)
- Public DEV: `https://trading-portal-dev.delena.buzz` (nginx → static UI + API `:3340`)

## Deploy topology (known facts below - cross-check against what you find in-repo, note any discrepancy explicitly rather than silently picking one)

**External baseline (given):** DEV API `:3340`, Angular UI `:3341`, Python MT5 XAUUSD ingest `:3342` (not a persistent daemon by default); PREPROD API `:4340` + UI `:4341` (paper-only), worker `:4342` reserved; PROD API `:5340` + UI `:5341` (paper-only), worker `:5342` reserved. Auth: CSS JWKS-ready, **dev-bypass currently**. Live trade execution stays fail-closed pending an explicit human unlock phrase.

**In-repo cross-check:**

| Fact | Matches? | Evidence |
|------|----------|----------|
| DEV `:3340` / `:3341` / `:3342` | Yes | `application.properties` `server.port=3340`; `frontend/package.json` `ng serve … --port 3341`; ingest health default DEV `3342` (`python/README.md`) |
| PREPROD `:4340` / `:4341` / `:4342` | Yes | `application-preprod.properties` port `4340`; `scripts/start-preprod.ps1` `$uiPort = 4341`; ingest PREPROD `:4342` |
| PROD `:5340` / `:5341` / `:5342` | Yes | `application-prod.properties` port `5340`; `scripts/start-prod.ps1` `$uiPort = 5341`; ingest PROD `:5342` |
| PREPROD/PROD paper-only | Yes | `trading.exec.live-enabled=false`, `broker=none` on preprod/prod; `docs/DEPLOY.md` “paper trading only · P5 micro-live HOLD” |
| Worker not persistent by default | Yes | `python/README.md`: one-shot unless `-ExtraArgs '--daemon'` |
| Live execution fail-closed / unlock phrase | Yes | `live-enabled=false` all profiles; README / FEATURE-VALIDATION / `agents/hires/P5-UNLOCK-STATUS-2026-07-17.md` require exact unlock phrase |
| Auth “JWKS-ready, **dev-bypass currently**” | **Discrepancy** | All checked profiles set `trading.security.dev-bypass=false` (JWKS path). DEV JWKS URI is css-next **`:4910`** (`application-dev.properties`). Optional bypass exists only if an operator sets `dev-bypass=true` + `X-Dev-Token` (`docs/OPS.md` §5). Frontend local `environment.ts` still points `cssUrl` at classic CSS **`:9000`**. |
| CSS port consistency | **Doc drift** | `docs/DEPLOY.md` table lists DEV CSS `:9000`; OPS/README public DEV cite css-next `:4910`; base `application.properties` comment still says CSS `:9000` while `application-dev.properties` uses `:4910`. PREPROD/PROD JWKS: `:4910` / `:5910` in properties. |
| Live version in DEPLOY.md | **Doc drift** | `docs/DEPLOY.md` still says “Version live: **0.2.0**”; README / OPS / pom / package.json / OpenAPI / start scripts jar name say **0.3.2**. |

Public traffic uses nginx static UI + `/api` + `/auth` (loopback UI ports optional per `docs/DEPLOY.md`).

## Known debt / gaps (as-is, factual)

- **P5 micro-live:** coded but fail-closed (`live-enabled=false`, broker `none`); arming HOLD until exact unlock phrase (`docs/FEATURE-VALIDATION-0.3.1.md`, `agents/hires/P5-UNLOCK-STATUS-2026-07-17.md`).
- **Open calibration (unchecked):** ATR `time_scale` quartile sweeps, So9 step sweeps, confluence weight walk-forward — tracked in `docs/calibration/OPEN-CALIBRATION.md`; defaults unchanged; latest backtests report no claimable edge.
- **README “Next”:** MT5 OHLC backfill (terminal IPC timing out historically), paper calibration after non-zero A/A+ sample, promote gated on Reviewer GO.
- **Gann Square-of-9 time (degrees → days):** explicitly deferred / not implemented in `MultiDayCycleCalculator` (class comment).
- **Docs inconsistency:** `docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md` §9 still titled “Backtesting Algorithm — **CRITICAL MISSING**” while FEATURE-VALIDATION / OPS list backtester as **IMPLEMENTED** (`POST /api/backtest/run`).
- **Stale deploy doc:** `docs/DEPLOY.md` version **0.2.0** vs tree **0.3.2**; CSS DEV port table vs css-next `:4910`.
- **Frontend local CSS URL** still `:9000` while DEV API profile JWKS is `:4910`.
- **OPS §12** still mentions CSS DEV `:9000` / older auth notes alongside the `:4910` guidance earlier in the same file.
- **Python README** still describes early “backend not scaffolded / no Flyway yet” historical state; Flyway `V1`/`V2` now exist in backend (note as stale narrative, not a missing migration).
- **Tests present but limited surface:** backend has a test suite (FEATURE-VALIDATION cites **124** tests); frontend Playwright e2e specs exist; Python has at least `test_mt5_init_timeout.py` — not claiming full coverage of all API/UI paths.

## Sources consulted

- `README.md`
- `docs/DEPLOY.md`
- `docs/OPS.md`
- `docs/FEATURE-VALIDATION-0.3.1.md`
- `docs/calibration/OPEN-CALIBRATION.md`
- `docs/contracts/openapi.yaml` (header / info)
- `docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md` (§9 heading region)
- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-dev.properties`
- `backend/src/main/resources/application-preprod.properties`
- `backend/src/main/resources/application-prod.properties`
- `frontend/package.json`
- `frontend/README.md`
- `frontend/src/app/app.routes.ts`
- `frontend/src/environments/environment.ts`
- `frontend/src/environments/environment.public-dev.ts`
- `frontend/src/environments/environment.preprod.ts`
- `frontend/src/environments/environment.prod.ts`
- `python/README.md`
- `python/requirements.txt`
- `scripts/start-preprod.ps1`
- `scripts/start-prod.ps1`
- `scripts/check-fleet.ps1` (CSS login host ports)
- `agents/hires/P5-UNLOCK-STATUS-2026-07-17.md` (unlock-phrase HOLD evidence via prior grep)
- `backend/src/main/java/com/delena/tradingportal/engine/gann/MultiDayCycleCalculator.java` (deferred So9 time comment)
