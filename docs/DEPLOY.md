# DEPLOY — Trading Portal

| Env | Path | API | UI | CSS JWKS | Start |
|-----|------|-----|-----|----------|-------|
| DEV | `E:\MyWorkspace\trading-portal` | 3340 | 3341 | `:9000` | `scripts/run-api-dev.ps1` + `frontend/scripts/run-ui-dev.ps1` |
| PREPROD | `F:\apps\trading-portal` | 4340 | 4341 | `:4900` (classic) | `powershell -File start.ps1` |
| PROD | `G:\apps\trading-portal` | 5340 | 5341 | `:5900` (classic) | `powershell -File start.ps1` |

**Version live:** 0.1.0 · git tag `v0.1.0`  
**Auth:** `clientId=trading-portal` · demo login `admin` / `admin123`  
**Scope:** paper trading only — no live broker adapter  

Release evidence: `H:\releases\trading-portal-0.1.0\`
