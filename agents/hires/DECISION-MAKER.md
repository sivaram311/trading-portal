# Decision maker change — Trading Portal

**Effective:** 2026-07-15 ~10:33 IST  
**Session:** `trading-portal-build-2026-07-15`

## Policy

| Before | After |
|--------|--------|
| Grok sole decision maker (`GOOD TO PROMOTE Q1/Q2` phrases) | **Human (user) confirms** — Lead does not wait on Grok CLI |

Lead still follows machine CONSCIOUS rules (ports, CSS, evidence packs, EM promote crew, Reviewer before push, Playwright slot, schema-per-env). Those are not waived unless the user explicitly waives a specific gate.

## Pending user confirmation (ask explicitly)

- [ ] **Q1** DEV → PREPROD (F:) for `trading-portal` `0.1.0`
- [ ] **Q2** PREPROD → PROD (G:) — note: no PREPROD soak yet; confirm if you want Q2 same day anyway

Evidence ready: `H:\releases\trading-portal-0.1.0\evidence\q1\` · SIGN-OFF GO · Device Lab 9/9 · live DEV stack.
