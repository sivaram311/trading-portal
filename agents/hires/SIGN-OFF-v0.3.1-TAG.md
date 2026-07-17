# SIGN-OFF — trading-portal v0.3.1 tag + Q1/Q2 promote push

| Field | Value |
|-------|-------|
| Session | trading-portal-0.3.1-promote-2026-07-17 |
| Reviewer | lead readonly (CONSCIOUS #17) |
| Tip | version bump + start.ps1 pin to 0.3.1; feature tip includes P5 `5270aea` |
| Branch / tag | `main` + annotated `v0.3.1` |
| When | 2026-07-17 ~11:05 IST |

## Checklist

- [x] Docs / evidence pack under `H:\releases\trading-portal-0.3.1\`
- [x] No secrets in commit
- [x] DEV E2E #16 green — public host Device Lab **9/9** (`evidence/e2e/`)
- [x] Login E2E #18 used `https://trading-portal-dev.delena.buzz`
- [x] `live-enabled=false` on F/G; P5 coded but **not armed** on preprod/prod
- [x] DEPENDENCIES.md records app tag + CSS ref `v0.2.1-5-g9b44a23`
- [x] Tag ≠ live broker — paper + fail-closed P5 roll only

## Verdict

**GO** — push `main` + tag `v0.3.1`; deploy to F: then G: with `live-enabled=false`.
