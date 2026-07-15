# GROK DECISION 001 — Trading Portal

**Session:** `trading-portal-build-2026-07-15`  
**Decision maker:** Grok (`cursor-grok-4.5-high`) — sole decision maker  
**Request:** `agents/hires/GROK-DECISION-REQUEST-001.md`  
**Date (IST):** 2026-07-15 ~09:10  
**Inputs read:** `docs/theory/*`, `docs/algorithms/*`, `agents/pre-work/01-vision-walkthrough.md`, `E:\Source\grok_dev\README.md`, MyAgent ports/DB/CSS SoT

---

## VERDICT
APPROVE_CODING: GO  
REASON: Theory/algorithms/vision P0 is sufficient north star. User ordered full implement-to-promote loop with Grok as sole decision maker. Pre-work is **compressed (not skipped)**: Architect must reserve ports/DB/CSS and land thin `02-architecture.md` + contracts **before first app listen**; design system is deferred to UI hire using vision §5. Live broker execution is **explicitly out of MVP**. Thin vertical slice (ICT+Gann confluence → paper pipeline → CSS portal) unlocks coding now; Lead re-asks Grok at milestones until **GOOD TO PROMOTE** or **STOP**.

---

## MVP SCOPE (must be implementable on DEV this session wave)
- include:
  - XAUUSD only (M1/M5/M15/H1 minimum; H4/D1 optional for HTF bias)
  - MT5 → Postgres OHLC ingest (Python worker)
  - ICT engine subset: sessions/killzones, liquidity sweep, MSS/BOS, FVG/OB zone, premium/discount → `IctSnapshot`
  - Gann engine subset: session pivots, 1×1 angle, Square-of-9 levels, basic time-window / stretch → `GannSnapshot`
  - Confluence layer → graded `ConfluenceDecision` with reason codes + mandatory invalidation (per `CONFLUENCE-FRAMEWORK.md`)
  - Risk gate (size, daily loss halt, max 1 open paper position, CONFLICT/news stub veto)
  - **Paper journal only** — confirm-always for entries (auto paper fill feature flag **OFF**)
  - Operator portal: CSS login + live confluence first viewport (grade/direction/mode + one reason + Confirm/Dismiss/Journal) + journal list
  - Health endpoints + `ny_time` correctness tests
- exclude (explicit):
  - Live / micro-live broker order routing (requires later Grok GO)
  - Multi-asset, copy-trading, SaaS multi-tenant
  - Full grok_dev feature parity (Analysis Lab, volatility explorer, market grid DnD, etc.)
  - Blind fork of grok_dev ports/schemas/`clientId`
  - Guaranteed-profit claims or signal-sale surfaces
  - Auto-entry without operator confirm (MVP default)

---

## STACK DECISION
backend: **Spring Boot 3.3** + Spring Data JPA + PostgreSQL; JWT validation via **CSS JWKS** (no local IdP); owns REST API, risk gate, paper journal, engine orchestration/serving  
frontend: **Angular 18** + Tailwind + Playwright e2e (patterns from grok_dev; **new** app tree — not a rename/fork)  
engines: **Java modules inside backend** implementing `docs/algorithms/*` (pure compute over stored OHLC); keep algorithm docs as SoT — do not invent silent weight changes without version bump  
data: **Python** under `python/` — MT5 XAUUSD downloader/daemon → `app_trading_portal` schema only; reference `E:\Source\grok_dev` + nearby `mt5-dev` for lessons, new package names  
auth: **CSS yes** — register `clientId=trading-portal` in MyAgent `workflow/css/CLIENT-REGISTRY.md`; DEV IdP `:9000`; frontend login with that clientId; API validates Bearer via JWKS + audience/client match

---

## PORTS (propose free set; Lead will reserve)
App block index **N=340** (free vs current SoT 3010/3080/3091/3310–3312/3320/3330):

| Env | API | UI | Worker (ingest) |
|-----|-----|-----|-----------------|
| DEV | **3340** | **3341** | **3342** |
| PREPROD | **4340** | **4341** | **4342** |
| PROD | **5340** | **5341** | **5342** |

App IDs: `trading-portal-api`, `trading-portal-ui`, `trading-portal-worker` (or single `trading-portal` with role columns — Lead’s choice so long as ports match).  
Do **not** bind until MyAgent `workflow/ports/REGISTRY.md` + `registry.json` (+ hub PORT-REGISTRY if required) are updated.

---

## DB
app id / database name / schemas:  
- **App ID:** `trading-portal`  
- **Database:** `app_trading_portal`  
- **Schemas:** `dev` | `preprod` | `prod`  
- **Roles:** `app_trading_portal_dev` | `app_trading_portal_preprod` | `app_trading_portal_prod`  
Reserve in `E:\MyAgent\workflow\db/` **before** DDL. E:→`dev` only.

---

## PHASE PLAN (ordered)
P0: Theory + algorithms + vision — **DONE** (2026-07-15)  
P1: Architect reserves ports/DB/CSS; thin `agents/pre-work/02-architecture.md` + `docs/contracts/` (decision + OHLC + journal APIs); Lead lands `approval.md` GO (this decision)  
P2: DEV vertical slice — ingest → engines → confluence → risk → paper journal APIs on `:3340`  
P3: Angular portal on `:3341` + CSS auth + Device Lab E2E (360×780 / 1280×800 / 800×1280); Playwright slot claim/release; DEV-host login if public hostname exists  
P4: Hardening — journal replay, weight version freeze, ops docs (`docs/OPS.md`), git tag readiness  
P5: **Micro-live adapter** — only after separate Grok decision (not this GO)

Promote gate criteria (what must be true for you to say GOOD TO PROMOTE Q1/Q2)

**Q1 (DEV → PREPROD) — ask Grok when all true:**
- [ ] Ports/DB/CSS reserved and wired; no live order adapter enabled
- [ ] Paper path end-to-end on DEV: candle → snapshots → `ConfluenceDecision` → confirm → journal row with reason codes
- [ ] CONFLICT / risk deny never creates paper entries
- [ ] `ny_time` / session tests pass (incl. DST case)
- [ ] E2E Device Lab evidence (phone+desktop+tablet) + Playwright slot protocol followed; login via DEV public host if one exists
- [ ] Reviewer SIGN-OFF GO (#17) before push/tag; app tag + CSS dep pin recorded (#13)
- [ ] Evidence pack under `H:\releases\trading-portal-<ver>\evidence\q1\` ready for promote crew

**Q2 (PREPROD → PROD) — ask Grok when all true:**
- [ ] PREPROD soak: ≥ **30** journaled paper decisions **or** ≥ **10** trading days (whichever first), 100% reason codes
- [ ] Kill / halt daily-loss path demonstrated on PREPROD
- [ ] No silent live flip; deps matrix + CSS tag current; Q2 evidence + EM GO

**STOP conditions:** port/DB/CSS violation; live routing without Grok GO; missing Reviewer before push; concurrent Playwright; deletes without user confirm.

---

## HIRES (order Lead must launch)
1. **architect** | Lock topology; reserve ports `3340–3342` / `4340–4342` / `5340–5342`, DB `app_trading_portal`, CSS `clientId=trading-portal`; write thin `02-architecture.md` | Registries updated + arch doc committed in tree  
2. **contracts** | OpenAPI + JSON schemas for OHLC, IctSnapshot, GannSnapshot, ConfluenceDecision, RiskVerdict, PaperJournal | `docs/contracts/` reviewable; matches confluence framework  
3. **backend** | Spring Boot scaffold on `:3340`; CSS JWKS; Flyway/Liquibase DDL; health; paper APIs stub | `/actuator/health` or `/api/health` green against `dev` schema  
4. **python-ingest** | MT5 XAUUSD OHLC → Postgres `dev`; daemon on `:3342` health/metrics optional | Candles queryable for M5/M15/H1  
5. **engines** | Implement ICT + Gann + confluence + risk in backend per algorithm docs; versioned weights | Decision rows with grades + reason codes + invalidation  
6. **frontend** | Angular portal `:3341` — CSS login, confluence viewport, confirm/dismiss, journal | Phone-first 360×780; vision §5 composition  
7. **e2e** | Hire per `E2E-HIRE.md`; claim Playwright slot; Realme + desktop + tablet | Evidence folder PASS (or documented skips only for auth soft)  
8. **reviewer** | Readonly review before any `git push` / tag | `SIGN-OFF.md` GO  
9. **promote crew** (em/qa/security/review/ops + field-ops) | Only after Grok says **GOOD TO PROMOTE Q1/Q2** | Evidence + EM GO; no F/G without that phrase  

Re-ask Grok after: P2 slice demo, P3 E2E green, and before every promote ask.

---

## FIRST ACTIONS FOR LEAD (checklist)
- [ ] Record this decision in `agents/crew-activity.md` (done by Grok this turn)
- [ ] Treat `agents/pre-work/approval.md` as coding GO with listed remaining docs
- [ ] Hire **architect** first — reserve ports/DB/CSS; write `02-architecture.md`
- [ ] Hire **contracts** in parallel or immediately after architect starts
- [ ] Do **not** bind `:3340–3342` until registries show reserved
- [ ] Scaffold Spring + Angular + Python only after architect reservation lands
- [ ] Keep live execution code paths absent or hard-disabled behind non-compilable/default-off gate
- [ ] After UI/API exist: E2E hires + Playwright slot; then Reviewer before push
- [ ] Re-ask Grok with demo evidence before claiming GOOD TO PROMOTE

---

## RISKS / HOLDS
- **Session clock / DST bugs** — mandatory `ny_time` tests; HOLD promote if flaky  
- **Overfit weights** — freeze `confluence_weights_version`; journal honesty over curve-fit  
- **MT5 terminal dependency** — ingest fails closed; portal shows stale/health degrade, never fake confluence  
- **Scope creep from grok_dev** — reference only; reject Analysis Lab / grid parity in this wave  
- **Auth** — CSS only; do not reuse `grok-dev` clientId  
- **Live urge** — micro-live is HOLD until explicit later Grok GO after paper soak  
- **Parallel Playwright** — one machine slot; never concurrent  

**Decision status:** BINDING until superseded by `GROK-DECISION-002+` or **STOP**.
