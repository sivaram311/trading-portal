# GROK DECISION 002 — P2/P3 review (DEV slice)

**Session:** `trading-portal-build-2026-07-15`  
**Decision maker:** Grok (`cursor-grok-4.5-high`) — sole decision maker  
**Request:** `agents/hires/GROK-DECISION-REQUEST-002.md`  
**Prior binding:** `GROK-DECISION-001.md`  
**Date (IST):** 2026-07-15 ~10:05  
**Inspected:** backend engines + `SecurityConfig` + `PaperTradingService` + `RiskGate` + `ConfluenceEngine`; frontend confluence component + Playwright scaffold; `docs/OPS.md` + contracts; live `:3340` / `:3342`; attempted `:3341` / CSS `:9000`; `mvn test` green

---

## VERDICT
CONTINUE_DEV

REASON: P2 backend vertical slice on `:3340` is real and paper-safe (graded confluence, reason codes, `invalid_if`, risk deny → no fill, live exec absent). Python ingest `:3342` seed health is green; MT5 correctly fail-closed. Angular UI is built (`dist/` present) but **not listening** on `:3341` at inspection time; CSS IdP `:9000` is **down** so auth is `dev-bypass` only. **Q1 promote gates are not met:** Device Lab E2E (slot + evidence), Reviewer SIGN-OFF (#17), and `H:\releases\trading-portal-<ver>\evidence\q1\` are all missing. Do **not** hire promote crew. Do **not** push/tag.

---

## MUST-FIX (Lead must complete before next ask)
1. **Serve UI on `:3341` and prove it** — start `scripts/run-ui-dev.ps1` (or equivalent); `GET http://127.0.0.1:3341/` returns 200; flip MyAgent port `3341` `reserved`→`active` only after verified.
2. **Hire e2e (Device Lab)** — claim Playwright slot per E2E-HIRE; run phone **360×780** + desktop **1280×800** + tablet **800×1280**; capture evidence folder PASS (auth soft-skips only where CSS truly unreachable — not as a blanket waive of confluence/journal flows).
3. **CSS auth path** — restore CSS IdP `:9000` (or equivalent DEV JWKS); set `trading.security.dev-bypass=false`; prove login with `clientId=trading-portal` against real JWT (API resource-server + UI). Dev-bypass alone is **not** enough for a promote ask.
4. **Reviewer hire** — readonly `#17` SIGN-OFF GO **before any `git push` / tag**; record app tag + CSS dep pin (`#13`) when tagging.
5. **Q1 evidence pack** — land `H:\releases\trading-portal-<ver>\evidence\q1\` with health/confluence/paper confirm+deny, `ny_time` DST, E2E Device Lab, Reviewer SIGN-OFF, registry screenshots/notes. No pack → no promote ask.

---

## SHOULD-FIX
1. Refresh MyAgent `SCHEMA-REGISTRY.md` — still says “no DDL yet”; Flyway `V1__init` + 6 tables are live in `app_trading_portal.dev`.
2. Keep `mt5:false` / ingest fail-closed until a real MT5 terminal is available; do not fake `mt5` source rows (current behavior is correct — preserve it).
3. If a public DEV hostname exists, wire nginx + CSS redirect URIs and use that host for E2E login (DECISION-001 Q1); if none exists, document localhost-only DEV explicitly in evidence.
4. Align crew-activity vs registry: frontend hire claimed done while `:3341` remained reserved/unbound — treat “done” only when listen+smoke verified.
5. Confirm journal always persists full reason-code arrays on PAPER_OPEN/DISMISSED (live row looked good; keep a regression assert in tests or E2E).

---

## NICE
1. Optional H4/D1 HTF bars for ICT premium/discount bias (MVP allows subset).
2. Broader engine unit coverage beyond CONFLICT / risk-deny happy paths (grade floors, midday cap, news veto integration).
3. Persistent ingest daemon (optional); seed + on-demand health is acceptable for DEV.

---

## PROMOTE CHECKLIST STATUS (mark each [x]/ [ ] from DECISION-001 Q1 list)
- [x] Ports/DB/CSS reserved and wired; no live order adapter enabled  
  _(Ports 3340/41/42 + 434x/534x reserved; API `:3340` + worker `:3342` active; DB `app_trading_portal`/`dev` Flyway live; CSS `clientId=trading-portal` registered; JWKS code path present; `trading.exec.live-enabled=false` and no broker adapter. **Caveat:** CSS IdP down → runtime uses `dev-bypass`; UI `:3341` not listening at inspection — see MUST-FIX 1+3.)_
- [x] Paper path end-to-end on DEV: candle → snapshots → `ConfluenceDecision` → confirm → journal row with reason codes  
  _(Live `:3340`: health ok/db true; confluence grade **A** / ALIGN_LONG / mode **T** / `weights_version=v1` / `invalid_if` present; journal row `PAPER_OPEN` with ICT_*+GANN_*+ALIGN_LONG reasons; re-confirm → 409 `MAX_OPEN_POSITIONS`.)_
- [x] CONFLICT / risk deny never creates paper entries  
  _(Unit: `ConfluenceEngineTest` CONFLICT→NONE/F/deny not confirmable; `RiskGateTest` deny / max-open / daily-loss; live confirm blocked when open position exists. `PaperDecisionPolicy` fail-closed.)_
- [x] `ny_time` / session tests pass (incl. DST case)  
  _(`NyTimeTest` + live `GET /api/health/ny-time` — all DST spring-forward / fall-back / UTC−4/−5 checks pass; `mvn test` green.)_
- [ ] E2E Device Lab evidence (phone+desktop+tablet) + Playwright slot protocol followed; login via DEV public host if one exists  
  _(Scaffold only: `frontend/e2e/confluence.spec.ts` + projects in `playwright.config.ts`. No slot claim, no evidence run, UI not up.)_
- [ ] Reviewer SIGN-OFF GO (#17) before push/tag; app tag + CSS dep pin recorded (#13)  
  _(No `SIGN-OFF.md`; no app tag.)_
- [ ] Evidence pack under `H:\releases\trading-portal-<ver>\evidence\q1\` ready for promote crew  
  _(`H:\releases` has other apps; **no** `trading-portal-*` evidence tree.)_

**Q2 checklist:** not started (no PREPROD soak). Do not ask Q2.

---

## NEXT HIRES
1. **e2e** — Device Lab + Playwright slot; evidence under project + copy into Q1 pack  
2. **CSS / platform** (if needed) — bring IdP `:9000` up so JWKS login is real  
3. **reviewer** — SIGN-OFF before push/tag  
4. **promote crew** — **FORBIDDEN** until a later decision says `GOOD TO PROMOTE Q1`  
5. Do **not** hire micro-live / broker adapter (still HOLD per DECISION-001 P5)

---

## RE-ASK TRIGGER
When Lead completes **all MUST-FIX** items (UI live, E2E evidence with slot protocol, CSS JWKS login proven with bypass off, Reviewer SIGN-OFF, Q1 evidence pack on `H:`), open **DECISION-003** request. Until then: **CONTINUE_DEV**; no promote hire; no push/tag; no live routing.

**Decision status:** BINDING until superseded by `GROK-DECISION-003+` or **STOP**.
