# Trading Portal — Frontend (Angular 18 + Tailwind)

Operator console for the Trading Portal MVP: ICT + Gann **confluence** for XAUUSD,
**paper-first**. Phone-first (comfortable at 360px), atmospheric dark theme.

- **Decision:** `../agents/hires/GROK-DECISION-001.md`
- **Vision §5 (composition):** `../agents/pre-work/01-vision-walkthrough.md`
- **API contract:** `../docs/contracts/openapi.yaml`

## Ports & services (DEV)

| Thing | URL |
|-------|-----|
| UI (this app) | `http://127.0.0.1:3341` |
| API (Spring Boot) | `http://127.0.0.1:3340` |
| CSS DEV IdP | `http://127.0.0.1:9000` (`clientId=trading-portal`) |

Configure in `src/environments/environment.ts` (`apiUrl`, `cssUrl`, `clientId`, `devToken`).

## Routes

| Route | View |
|-------|------|
| `/login` | CSS password login (`POST {cssUrl}/auth/login {username,password,clientId}`) → stores JWT. Clear error if CSS unreachable; optional `DEV_TOKEN` demo button. |
| `/` | **Live confluence** — brand · style badge (DAY/SCALP/POSITIONAL) · headline · price-rail overlays · engine tags · Confirm/Dismiss/Journal |
| `/journal` | Paper journal — grade filters; exit/MFE/MAE when closed |

`/` and `/journal` are protected by `authGuard`.

## Graceful degradation

If the API (`:3340`) is down, reads fall back to deterministic **mock** fixtures and a
banner is shown; Confirm/Dismiss are simulated locally (not journaled server-side).
Confirm is disabled for `automation=deny`, `grade=F`, or `mode=NONE` (fail-closed).

## Run

```bash
npm install
npm start            # ng serve on 127.0.0.1:3341
npm run build        # production build -> dist/trading-portal
```

Or from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ui-dev.ps1
```

## E2E (Playwright)

```bash
npm run e2e:install  # one-time: chromium
npm run e2e          # 12 tests: auth + journal + ICT/Gann reason chips (3 viewports)
```

Specs **soft-skip** authenticated views when CSS is unreachable (no real JWT).
Full Device Lab evidence is captured by the dedicated e2e hire (Playwright slot
claim/release per `E2E-HIRE.md`).

## Design notes

- **Fonts:** Fraunces (display), Archivo (body), JetBrains Mono (numbers) — deliberately not Inter/Roboto/Arial/system stacks.
- **Palette:** obsidian + gold (XAUUSD) with bull-teal / bear-rose direction cues; atmospheric radial glows + faint grain. No purple-on-white, no cream+terracotta.
- **One composition** per first viewport; the price-levels rail (SVG) is the dominant visual, not a card wall.
