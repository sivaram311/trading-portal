# E2E — Public DEV host (Roadmap 0.2)

**baseURL:** `https://trading-portal-dev.delena.buzz` (CONSCIOUS #18)  
**Session:** `trading-portal-roadmap-0.2-e2e-retry-2026-07-16`  
**When:** 2026-07-16 ~21:02 IST  

## Slot (#15)

| Step | Status |
|------|--------|
| Claim | OK |
| Release | `pass` |

## Result

**9/9 PASS** (phone 360×780 · desktop 1280×800 · tablet 800×1280)

| Spec | Result |
|------|--------|
| CSS login → confluence composition | PASS ×3 |
| Journal after login | PASS ×3 |
| Unauthenticated → `/login` | PASS ×3 |

## Auth note

Legacy `admin123` is **rejected** after CSS password-align (`E:\MyAgent\workflow\css\PASSWORD-SOT.md`).  
E2E must set `TP_E2E_PASS` (or `CSS_ADMIN_PASSWORD`) from `G:\apps\css\.env` — never commit the secret.

```powershell
$env:TP_BASE_URL = 'https://trading-portal-dev.delena.buzz'
# load TP_E2E_PASS from CSS_ADMIN_PASSWORD SoT, then:
npx playwright test --reporter=list
```

## Earlier attempt (same day)

First run with `admin123` → **3 pass / 6 fail** (login). Evidence superseded by this retry. Transient `/api/health` 502 during API restart; health **200** after DEV API restart.

## Related

- Loopback Device Lab (0.1.0): prior evidence under release pack  
- Soak: `GET /api/ops/soak` on DEV after Bearer → `soak_met=false` (1 decision / 1 day) — PREPROD accumulation still required for DECISION-001 soak gate
