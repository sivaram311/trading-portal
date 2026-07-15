# Pre-work approval — Trading Portal

**Status:** **GO** (coding approved under compressed pre-work)  
**Decision:** `agents/hires/GROK-DECISION-001.md`  
**Decision maker:** Grok (sole) — session `trading-portal-build-2026-07-15`  
**Date (IST):** 2026-07-15  

## Waiver / GO

Grok sets **APPROVE_CODING=GO**. Full persona pre-work chain is **compressed**, not abandoned:

| Doc | Status |
|-----|--------|
| `01-vision-walkthrough.md` | **Required — exists** (accept as P0 north star) |
| `02-architecture.md` | **Required before first port bind** — Architect hire lands thin topology (stack/ports/DB/CSS) |
| `docs/contracts/` | **Required before engine/API implementation beyond health stubs** |
| Design system / full UX kit | **Deferred** — UI hire follows vision §5 + frontend design rules; no separate design GO blocking scaffold |
| This `approval.md` | **GO recorded** |

## Coding allowed now for

- Registry reservations (ports / DB / CSS `clientId=trading-portal`)
- Spring Boot API `:3340`, Angular UI `:3341`, Python ingest worker `:3342` (after reserve)
- ICT + Gann + confluence + risk + **paper** journal vertical slice
- CSS auth wiring
- Device Lab E2E when UI/API exist

## Still forbidden without later Grok GO

- Live / micro-live broker execution
- Promote to PREPROD/PROD until Grok says **GOOD TO PROMOTE Q1/Q2**
- `git push` / tag without Reviewer SIGN-OFF (#17)

## Stack / ports / DB (locked by DECISION-001)

- Stack: Spring Boot 3.3 + Angular 18 + Python MT5 ingest; engines in Java backend  
- DEV ports: API **3340**, UI **3341**, worker **3342** (PREPROD 434x / PROD 534x)  
- DB: `app_trading_portal` schemas `dev|preprod|prod`  
- Auth: CSS `clientId=trading-portal`
