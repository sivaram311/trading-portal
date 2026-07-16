# SOAK baseline — 0.2.0 (parallel track)

**Status:** open accumulation on PREPROD after Q1 promote  
**Does not block:** tag, push, or Q1/Q2 promote (soak waiver on record for 0.2.0)  
**Gate (DECISION-001):** ≥ **30** distinct journaled decisions **or** ≥ **10** distinct `session_date` days — whichever first (`soak_met=true`).

---

## When to run

After **Q1** deploys `v0.2.0` to F: PREPROD (`F:\apps\trading-portal`, API `:4340`, schema `preprod`).

Public host (nginx `/api` → loopback `:4340`):

| Host | CSS (via `/auth`) |
|------|-------------------|
| https://trading-portal-staging.delena.buzz | css-next `:4910` |

Record the **first** soak snapshot as the 0.2.0 baseline (expect low counts right after deploy).

---

## 1. Obtain Bearer (staging)

Password from CSS SoT (`G:\apps\css\.env` / `CSS_ADMIN_PASSWORD`) — never commit.

```powershell
$staging = 'https://trading-portal-staging.delena.buzz'
$loginBody = @{
  username = 'admin'
  password = $env:CSS_ADMIN_PASSWORD   # load from SoT first
  clientId = 'trading-portal'
} | ConvertTo-Json

$token = (Invoke-RestMethod -Method POST -Uri "$staging/auth/login" `
  -ContentType 'application/json' -Body $loginBody).accessToken

$h = @{ Authorization = "Bearer $token" }
```

Loopback alternative (same PREPROD API, no nginx):

```powershell
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Headers $h http://127.0.0.1:4340/api/ops/soak
```

---

## 2. GET `/api/ops/soak`

```powershell
$soak = Invoke-RestMethod -Headers $h "$staging/api/ops/soak"
$soak | ConvertTo-Json -Depth 5
```

**Example response shape:**

```json
{
  "journalDecisionCount": 1,
  "distinctSessionDays": 1,
  "paperOpenCount": 1,
  "alertedCount": 0,
  "rejectedCount": 0,
  "weightsVersions": ["v1"],
  "soakTarget": { "minDecisions": 30, "minSessionDays": 10 },
  "soakMet": false
}
```

| Field | Meaning |
|-------|---------|
| `journalDecisionCount` | Distinct decisions journaled (primary counter) |
| `distinctSessionDays` | Distinct NY `session_date` values in journal |
| `soakMet` | `true` when count ≥ **30** **or** days ≥ **10** |
| `soakTarget` | Fixed targets `{30, 10}` from `OpsService` |

Companion audit: `GET /api/ops/weights` (configured + distinct `weights_version` seen).

---

## 3. Baseline checklist (after Q1)

- [ ] Staging health public: `GET https://trading-portal-staging.delena.buzz/api/health` → `ok`
- [ ] Authenticated soak: `GET .../api/ops/soak` → 200 + JSON above
- [ ] Save snapshot (counts + timestamp) under `H:\releases\trading-portal-0.2.0\evidence\soak-baseline\` (or crew note)
- [ ] If MT5 unavailable (`docs/OPS.md` § MT5 status 0.2.0): label baseline **seed-backed**; optional ingest `F:\...\python\scripts\run-ingest-preprod.ps1 -Mode seed`

---

## 4. Accumulation (paper-only)

- Normal operator flow: confluence → **Confirm Paper** / dismiss → journal rows accrue.
- Ops replay (authenticated): `POST /api/ops/replay?asof=2026-07-16T12:00:00Z` recomputes from stored OHLC (same pipeline as startup).
- **Forbidden:** live execution, `trading.exec.live-enabled=true`, P5 adapter (`agents/hires/P5-MICRO-LIVE-HOLD.md`).

Re-check soak weekly or before closing the parallel track:

```powershell
Invoke-RestMethod -Headers $h "$staging/api/ops/soak" | Select-Object journalDecisionCount, distinctSessionDays, soakMet
```

---

## Related

- Runbook: `docs/OPS.md` § 8b PREPROD paper soak
- Deploy map: `docs/DEPLOY.md`
- Promote waiver: `agents/hires/AGY-CONFIRM-0.2.md`
