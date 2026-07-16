# DEPLOY — Trading Portal

| Env | Path | API | UI | Public hostname | CSS (via nginx `/auth`) |
|-----|------|-----|-----|-----------------|-------------------------|
| DEV | `E:\MyWorkspace\trading-portal` | 3340 | static `frontend/dist/public-dev` | https://trading-portal-dev.delena.buzz | `:9000` |
| PREPROD | `F:\apps\trading-portal` | 4340 | `app/ui` | https://trading-portal-staging.delena.buzz | css-next `:4910` |
| PROD | `G:\apps\trading-portal` | 5340 | `app/ui` | https://trading-portal.delena.buzz | css-next `:5910` |

**Version live:** 0.2.0 · git tag `v0.2.0`  
**Auth:** `clientId=trading-portal` · admin password = CSS SoT `CSS_ADMIN_PASSWORD` (legacy `admin123` rejected)  
**Edge:** Cloudflare A proxied → origin `103.118.183.185` → nginx `:80` (Flexible SSL)  
**Nginx confs:** `E:\Source\Deployment\conf\apps\trading-portal*.delena.buzz.conf` (also under `C:\nginx-1.30.3\conf\apps\`)  
**Scope:** paper trading only · P5 micro-live HOLD  

Local loopback UI ports 3341/4341/5341 optional for operators; public traffic uses nginx static + `/api` + `/auth`.

Release evidence: `H:\releases\trading-portal-0.2.0\`
