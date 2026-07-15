# DEPLOY — Trading Portal

| Env | Path | API | UI | CSS JWKS / IdP | Start |
|-----|------|-----|-----|----------------|-------|
| DEV | `E:\MyWorkspace\trading-portal` | 3340 | 3341 | `:9000` (loopback; css-next public `https://css-next.delena.buzz` also OK) | `scripts/run-api-dev.ps1` + `frontend/scripts/run-ui-dev.ps1` |
| PREPROD | `F:\apps\trading-portal` | 4340 | 4341 | css-next `:4910` (`https://css-next-staging.delena.buzz`) | `powershell -File start.ps1` |
| PROD | `G:\apps\trading-portal` | 5340 | 5341 | css-next `:5910` / issuer `https://css-next.delena.buzz` | `powershell -File start.ps1` |

**Version live:** 0.1.0 (tip commit on tagged line — no patch bump for IdP flip)  
**Auth:** `clientId=trading-portal` · admin + `CSS_ADMIN_PASSWORD` from `G:\apps\css-next\.env` (not README `admin123` on F/G)  
**Scope:** paper trading only — no live broker adapter  

| UI env | `cssUrl` |
|--------|----------|
| `environment.ts` (DEV) | `http://127.0.0.1:9000` |
| `environment.preprod.ts` | `http://127.0.0.1:4910` |
| `environment.prod.ts` | `http://127.0.0.1:5910` |

Release evidence: `H:\releases\trading-portal-0.1.0\`  
css-next flip evidence: `H:\releases\trading-portal-0.1.0\evidence\css-next-flip\`
