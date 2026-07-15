# 02 — Architecture (thin) — Trading Portal

**Status:** Registries reserved; **no port bind, no DDL, no live execution**.
**Decision:** `agents/hires/GROK-DECISION-001.md` (binding) · **Session:** `trading-portal-build-2026-07-15`
**Hire:** architect · **Depends on:** `01-vision-walkthrough.md`, `docs/algorithms/AUTOMATION-PIPELINE.md`, MyAgent ports/DB/CSS SoT

---

## 1. Topology diagram

```text
                         ┌───────────────────────────────┐
                         │   Angular 18 UI  (:3341)      │
                         │   Operator portal — phone-first│
                         └───────────────┬────────────────┘
                                         │ HTTPS (Bearer JWT)
                                         ▼
┌────────────────────────────────────────────────────────────────┐
│                 Spring Boot 3.3 API  (:3340)                   │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐ ┌─────────┐ ┌──────┐ │
│  │ ingest.* │ │engine.ict│ │engine.gann │ │confluence│ │ risk │ │
│  │ (reader) │ │IctSnapshot│ │GannSnapshot│ │Decision │ │Verdict│ │
│  └────┬─────┘ └────┬─────┘ └─────┬──────┘ └────┬─────┘ └──┬───┘ │
│       │            └──────┬──────┘             │           │    │
│       │                   ▼                    ▼           ▼    │
│       │            ┌─────────────────────────────────────────┐  │
│       │            │           journal (paper only)          │  │
│       │            └─────────────────────────────────────────┘  │
│       │            CSS resource-server (JWKS validate, aud=trading-portal)│
└───────┼──────────────────────────────────────────────────────────┘
        │ writes OHLC
        ▼
┌────────────────────────────┐        ┌──────────────────────────┐
│ Python ingest worker(:3342)│──────▶ │ Postgres app_trading_portal│
│ MT5 → XAUUSD OHLC daemon   │        │ schema: dev|preprod|prod   │
└────────────────────────────┘        └──────────────────────────┘
                                                  ▲
                                                  │ JWKS
                         ┌────────────────────────┴───────┐
                         │  CSS  (:9000 DEV)  clientId=trading-portal │
                         └─────────────────────────────────┘
```

No component binds a port until MyAgent registries below show `reserved` (this doc) → `active` (after Lead/backend hire actually starts the process).

---

## 2. Stack (locked by GROK-DECISION-001 — do not silently change)

| Layer | Choice | Notes |
|-------|--------|-------|
| Backend | Spring Boot 3.3 + Spring Data JPA + PostgreSQL | Owns REST API, risk gate, paper journal, engine orchestration/serving |
| Frontend | Angular 18 + Tailwind + Playwright e2e | New app tree; patterns referenced from `E:\Source\grok_dev`, not a fork/rename |
| Engines | Java modules inside backend (`engine.ict`, `engine.gann`, `confluence`) | Pure compute over stored OHLC; implement `docs/algorithms/*` as SoT; version-bump on weight change |
| Ingest | Python under `python/` — MT5 XAUUSD downloader/daemon | Writes only to `app_trading_portal` schema for its env; new package names (ref `E:\Source\grok_dev`, `mt5-dev`) |
| Auth | CSS only — `clientId=trading-portal` | DEV IdP `:9000`; JWKS validation in API; Angular login redirects to CSS |
| DB | PostgreSQL shared instance `127.0.0.1:5432` | `app_trading_portal` / schema-per-env |

---

## 3. Ports (reserved this hire — status `reserved`, not `active`)

| Env | API | UI | Worker (ingest) | Registry status |
|-----|-----|-----|-----------------|------------------|
| DEV (E:) | **3340** | **3341** | **3342** | `reserved` |
| PREPROD (F:) | **4340** | **4341** | **4342** | `reserved` |
| PROD (G:) | **5340** | **5341** | **5342** | `reserved` |

App ID: `trading-portal` (single app id; ports differentiate role per CONSCIOUS rule 6 / `workflow/ports/README.md`).

Reserved in:
- `E:\MyAgent\workflow\ports\REGISTRY.md` (DEV/PREPROD/PROD rows added)
- `E:\MyAgent\workflow\ports\registry.json` (mirrored)
- Hub mirror: `E:\MyWorkspace\agent-portal\docs\platform\PORT-REGISTRY.md`

**Do not bind** until the backend/frontend/python-ingest hires actually start the process — reservation ≠ activation.

---

## 4. Database

| Item | Value |
|------|-------|
| Database | `app_trading_portal` |
| Schemas | `dev` \| `preprod` \| `prod` |
| Roles | `app_trading_portal_dev` \| `app_trading_portal_preprod` \| `app_trading_portal_prod` |
| Instance | shared `127.0.0.1:5432` |
| Status | `reserved` in `E:\MyAgent\workflow\db\SCHEMA-REGISTRY.md` + `registry.json` — **no DDL yet** |

DEV process uses `dev` schema only (E: → dev per hard rule). DDL/Flyway/Liquibase migration is the **backend** hire's job (P2), not this architecture doc.

Planned tables (names only, no DDL here): `ohlc_candle`, `ict_snapshot`, `gann_snapshot`, `confluence_decision`, `risk_verdict`, `paper_journal`.

---

## 5. CSS auth

| Item | Value |
|------|-------|
| clientId | `trading-portal` |
| Registered in | `E:\MyAgent\workflow\css\CLIENT-REGISTRY.md` (status `registered`) |
| DEV IdP | `http://127.0.0.1:9000` (profile `dev`, Postgres `app_css.dev`) |
| JWKS | `GET http://127.0.0.1:9000/.well-known/jwks.json` |
| Redirect URI (DEV placeholder) | `http://127.0.0.1:3341/**` (Angular dev server / SPA callback) |
| Future public host (TBD) | `https://trading-portal-dev.delena.buzz` (not live; add when DEV subdomain is created, per CONSCIOUS #18 for later login E2E) |
| API resource server | Validates Bearer JWT via JWKS; audience/clientId match `trading-portal`; prefer OAuth `/oauth/authorize` SSO pattern over embedded password form (`workflow/css/integration.md`) |
| Do not | Reuse `grok-dev` clientId; stand up a second IdP |

---

## 6. Module boundaries (backend, single Spring Boot deployable)

| Module | Responsibility | Depends on |
|--------|----------------|------------|
| `ingest` | Reads OHLC written by Python worker; exposes candle query API; validates `ny_time` | Postgres |
| `engine.ict` | Sessions/killzones, liquidity sweep, MSS/BOS, FVG/OB, premium/discount → `IctSnapshot` | `ingest`, `docs/algorithms/ICT-SIGNAL-ENGINE.md` |
| `engine.gann` | Session pivots, 1×1 angle, Square-of-9, time-window/stretch → `GannSnapshot` | `ingest`, `docs/algorithms/GANN-CYCLE-ENGINE.md` |
| `confluence` | Merge Ict+Gann snapshots → graded `ConfluenceDecision` (reason codes + invalidation) | `engine.ict`, `engine.gann`, `docs/theory/CONFLUENCE-FRAMEWORK.md` |
| `risk` | Size, daily loss halt, max 1 open paper position, CONFLICT/news veto → `RiskVerdict` | `confluence` |
| `journal` | Persist decision + outcome; **paper only**, auto-fill flag hard OFF | `risk` |
| `portal` (Angular) | CSS login, live confluence viewport, confirm/dismiss, journal list | `journal`, `confluence` APIs, CSS |
| `exec.adapter` | **Not implemented.** Placeholder package/interface may exist but must be non-compilable-active or default-off gated; requires separate future Grok GO | n/a |

Python `ingest-worker` is a **separate deployable** (own process on `:3342`), writing to the same `app_trading_portal` DB — not a Spring module.

Boundary rule: engines are pure functions over stored data + config (side-effect free except metrics/logs), per `AUTOMATION-PIPELINE.md` §3 Stage B.

---

## 7. Health endpoints (contract only — implemented by backend/python hires)

| Component | Endpoint | Purpose |
|-----------|----------|---------|
| API | `/actuator/health` or `/api/health` | DB reachable, CSS JWKS reachable |
| API | `/api/health/ny-time` | `ny_time` correctness self-check (DST case) |
| Python ingest | health/metrics file or lightweight `/health` (optional per DECISION-001) | Candle freshness, MT5 connection state; fails closed (never fakes confluence) |
| Angular | N/A (static) | Portal shows stale/health-degrade banner when API health is red |

---

## 8. Explicit non-goals (this hire)

- No port bind, no `listen()` calls
- No Flyway/Liquibase DDL execution
- No Spring/Angular/Python source files
- No live/micro-live execution adapter wiring
- No promote to F:/G:

---

## 9. Related

| Doc | Role |
|-----|------|
| `agents/hires/GROK-DECISION-001.md` | Binding decision this doc implements |
| `agents/pre-work/approval.md` | Coding GO record |
| `docs/algorithms/AUTOMATION-PIPELINE.md` | Pipeline stages this topology maps to |
| `docs/theory/CONFLUENCE-FRAMEWORK.md` | Confluence scoring rules |
| `E:\MyAgent\workflow\ports\REGISTRY.md` | Port SoT |
| `E:\MyAgent\workflow\db\SCHEMA-REGISTRY.md` | DB SoT |
| `E:\MyAgent\workflow\css\CLIENT-REGISTRY.md` | CSS clientId SoT |

**Next hire:** `contracts` — OpenAPI + JSON schemas for OHLC, IctSnapshot, GannSnapshot, ConfluenceDecision, RiskVerdict, PaperJournal (per DECISION-001 hire order #2).
