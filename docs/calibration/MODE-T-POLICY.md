# Mode T paper-confirm policy

**Date:** 2026-07-30  
**Status:** Enforced in code (paper-only)

## Spec

From `docs/theory/CONFLUENCE-FRAMEWORK.md` — Mode T (**Time-triggered watch**):

- Intent: elevate monitoring at cycle milestones; **not auto-entry alone**.
- Automation: **alert only** unless Mode **R** / **C** is also satisfied.

## Previous bug

`PaperDecisionPolicy` treated any non-`NONE` mode as actionable. Combined with `automation()` ignoring mode, a **Mode T + grade A** decision could become `automation=confirm` and paper-fill (v2 Jul 22 SCALP/DAY/POSITIONAL fills).

## Fix (v3)

1. `ConfluenceEngine.automation(...)` — Mode `T` / `NONE` → **`deny`** (never `confirm`).
2. `PaperDecisionPolicy` — Mode **`T`** is never confirmable even if automation were mislabeled `confirm`.

Actionable paper modes remain **R** and **C** only (still require grade A/A+, risk ok, non-deny automation).

## Calibration implication

- v2’s single Mode T fill is **invalid under theory** and must not count as edge.
- Rolling / future sweeps should only credit **R/C** confirms.
- To research Mode T fills intentionally, add an explicit opt-in flag later — default stays watch-only.
