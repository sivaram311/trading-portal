# SIGN-OFF — trading-portal v0.3.2 feature validation + V2 migrate

| Field | Value |
|-------|-------|
| Session | trading-portal-0.3.2-validate-2026-07-17 |
| Reviewer | lead readonly (CONSCIOUS #17) |
| When | 2026-07-17 ~11:40 IST |

## Checklist

- [x] Feature inventory honest (`/api/ops/status.features` + `docs/FEATURE-VALIDATION-0.3.1.md`)
- [x] DEV E2E 12/12 public host
- [x] Flyway V2 applied on `dev` / `preprod` / `prod`
- [x] F/G health ok · live=false · backtest API up
- [x] No secrets

## Verdict

**GO** — push `main` + tag `v0.3.2`.
