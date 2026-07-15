# GROK DECISION 003 — MUST-FIX re-check (promote readiness)

**Session:** `trading-portal-build-2026-07-15`  
**Decision maker:** Grok (`cursor-grok-4.5-high`) — sole decision maker  
**Request:** `agents/hires/GROK-DECISION-REQUEST-003.md`  
**Prior binding:** `GROK-DECISION-002.md` (CONTINUE_DEV)  
**Date (IST):** 2026-07-15 ~10:20  
**Inspected:** live `:3340` / `:3341` / `:3342` / CSS `:9000`; MyAgent ports/DB/CSS registries + hub PORT-REGISTRY; `playwright-slot.json`; `H:\releases\trading-portal-0.1.0\evidence\**`; `agents/hires/SIGN-OFF-0.1.0.md`; tree auth/exec flags

---

## VERDICT
CONTINUE_DEV

REASON: Most DECISION-002 MUST-FIX items are **real** (UI `:3341` 200; CSS JWKS up; `dev-bypass=false` enforced — unauth/dev-token/bad bearer → 401; `admin`/`admin123` + `clientId=trading-portal` → Bearer → confluence **grade A / ALIGN_LONG / mode T / weights v1**; paper journal `PAPER_OPEN` + re-confirm **409 MAX_OPEN**; Reviewer SIGN-OFF **GO**; Q1 pack present on `H:`). Device Lab **9/9 PASS** log is real. **However Q1 is not complete:** Playwright slot SoT does **not** corroborate Lead’s claim for session `trading-portal-build-2026-07-15`. `e2e-run.log` mtime **10:10:58** while `playwright-slot.json` `lastRelease` is **`css-migrate-pd-082-2026-07-15` at 10:12:05** — trading-portal’s run finished while another session’s hold was still the recorded world-state. Slot protocol (CONSCIOUS #15 / DECISION-001 Q1) is therefore **not proven**; concurrent/unclaimed run is the strict reading. Also `registry.json` still has UI **3341=`reserved`** (MD/hub say `active` with stale “not bound” notes). Do **not** hire promote crew. Do **not** say GOOD TO PROMOTE Q1. Do **not** say GOOD TO PROMOTE Q2 (no PREPROD soak).

---

## MUST-FIX (if any)
1. **Playwright slot — re-prove exclusivity** — `claim-playwright-slot.ps1` with `-SessionId trading-portal-build-2026-07-15` while status=`free`; re-run Device Lab (phone 360×780 + desktop 1280×800 + tablet 800×1280); `release-playwright-slot.ps1` `-Result pass`; confirm SoT `lastRelease.sessionId` (or contemporaneous holder evidence) is this session. Self-assert in `docs/E2E.md` alone is **insufficient**. Copy fresh log into `H:\releases\trading-portal-0.1.0\evidence\q1\e2e\`.
2. **Port registry truth for `:3341`** — flip MyAgent `workflow/ports/registry.json` `3341` `reserved`→`active`; rewrite notes in `REGISTRY.md` + hub `PORT-REGISTRY.md` to “UI listening verified GET / → 200” (drop “not bound yet”). JSON and MD must match.

---

## SHOULD-FIX
1. Refresh MyAgent `SCHEMA-REGISTRY.md` / `db/registry.json` — still “no DDL yet”; Flyway `V1__init` + tables are live in `app_trading_portal.dev`.
2. Update `docs/OPS.md` — still documents CSS unreachable + `dev-bypass=true`; live stack is JWKS with bypass off.
3. Harden Q1 pack: replace `paper-confirm-error.txt` (400 validation from wrong JSON field) with proven **409 MAX_OPEN_POSITIONS** capture (`decision_id` snake_case).
4. Pin CSS dep more tightly in `H:\releases\trading-portal-0.1.0\DEPENDENCIES.md` (CSS git tip / tag, not only tree path) when tagging.
5. Keep `application-local.properties` gitignored (verified not tracked); never stage DB password.

---

## PROMOTE CHECKLIST STATUS
From DECISION-001 Q1:

- [x] Ports/DB/CSS reserved and wired; no live order adapter enabled  
  _(3340/3342 active + listening; CSS `trading-portal` registered + DataSeeder seeds client; `trading.exec.live-enabled=false`; no broker adapter. **Gap:** `registry.json` 3341 still `reserved` despite live UI — see MUST-FIX 2.)_
- [x] Paper path end-to-end on DEV: candle → snapshots → `ConfluenceDecision` → confirm → journal row with reason codes  
  _(Live JWT path: journal `PAPER_OPEN` + ICT_*/GANN_*/ALIGN_LONG reasons; evidence `api/confluence-decision.json` + `paper-journal.json`.)_
- [x] CONFLICT / risk deny never creates paper entries  
  _(Prior unit coverage + live re-confirm → **409 MAX_OPEN_POSITIONS** with Bearer JWT this inspection.)_
- [x] `ny_time` / session tests pass (incl. DST case)  
  _(Evidence `api/ny-time.json` all DST checks pass; live `GET /api/health/ny-time` 200.)_
- [ ] E2E Device Lab evidence (phone+desktop+tablet) + Playwright slot protocol followed; login via DEV public host if one exists  
  _(9/9 PASS log + localhost-only DEV documented — **PASS content**. Slot protocol **FAIL / unverified** vs SoT — see MUST-FIX 1. No public DEV hostname — localhost OK if documented.)_
- [x] Reviewer SIGN-OFF GO (#17) before push/tag; app tag + CSS dep pin recorded (#13)  
  _(SIGN-OFF GO on `H:\…\evidence\review\SIGN-OFF.md` + `agents/hires/SIGN-OFF-0.1.0.md`. App tag correctly **pending** until promote GO. CSS dep noted in `DEPENDENCIES.md` — soft pin; tighten on tag.)_
- [x] Evidence pack under `H:\releases\trading-portal-<ver>\evidence\q1\` ready for promote crew  
  _(Pack exists: health, ny-time, confluence, journal, e2e, SUMMARY. Refresh e2e after slot re-run.)_

**Q2 checklist:** not started (no PREPROD soak ≥30 paper decisions / ≥10 trading days). **Forbidden** to say GOOD TO PROMOTE Q2.

---

## NEXT HIRES (promote crew only if GOOD TO PROMOTE Q1)
1. **e2e (re-hire / same lane)** — exclusive slot claim → Device Lab re-run → release → refresh H: e2e evidence  
2. **lead / registry hygiene** — sync port 3341 JSON+MD+hub notes  
3. **promote crew** — **FORBIDDEN** until a later decision uses the exact phrase **GOOD TO PROMOTE Q1**  
4. Do **not** hire micro-live / broker adapter (still HOLD per DECISION-001 P5)  
5. Do **not** push/tag until after a future GOOD TO PROMOTE Q1 (Reviewer already GO for readiness)

---

## RE-ASK TRIGGER
Open **DECISION-004** only when MUST-FIX 1+2 are proven in SoT (slot `lastRelease`/`holder` for `trading-portal-build-2026-07-15` + `registry.json` 3341=`active` with truthful notes) and e2e evidence on `H:` is refreshed. Until then: **CONTINUE_DEV**; no promote hire; no GOOD TO PROMOTE Q1; no GOOD TO PROMOTE Q2; no live routing.

**Decision status:** BINDING until superseded by `GROK-DECISION-004+` or **STOP**.
