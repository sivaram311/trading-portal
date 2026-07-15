# Contracts — Trading Portal

**Status:** v0.1.0 — API + data contract for the MVP paper-confluence vertical slice
(`agents/hires/GROK-DECISION-001.md`, phase P1). Documentation only — no Spring/Angular code,
no port binds, no DB/CSS registry writes.

This directory is the **single source of truth for wire shapes** between:

- Python ingest → Spring backend (`OhlcBar`)
- ICT / Gann engines → Confluence layer (`IctSnapshot`, `GannSnapshot`)
- Confluence layer → Risk gate → Journal (`ConfluenceDecision`, `RiskVerdict`, `PaperJournalEntry`)
- Backend → Angular portal (all of the above, over the REST API)

Field names are aligned 1:1 with the theory/algorithm docs — see each schema's `description`
for the source section it implements. Do not rename fields here without updating the SoT docs
first (`docs/theory/CONFLUENCE-FRAMEWORK.md`, `docs/algorithms/*`).

## Files

| File | Purpose |
|------|---------|
| [`openapi.yaml`](./openapi.yaml) | OpenAPI 3.1 spec for the DEV API (`http://localhost:3340`, `reserved` in the ports registry per `agents/pre-work/02-architecture.md` §3 — not yet `active`; contracts does not bind it). |
| [`schemas/ohlc-bar.json`](./schemas/ohlc-bar.json) | One OHLCV bar, `ny_time`-enriched. |
| [`schemas/ict-snapshot.json`](./schemas/ict-snapshot.json) | ICT Signal Engine output. |
| [`schemas/gann-snapshot.json`](./schemas/gann-snapshot.json) | Gann Cycle Engine output. |
| [`schemas/confluence-decision.json`](./schemas/confluence-decision.json) | Fused decision object (grade/mode/direction/invalidation). |
| [`schemas/risk-verdict.json`](./schemas/risk-verdict.json) | Risk gate outcome (size, deny reasons, per-rule checks). |
| [`schemas/paper-journal-entry.json`](./schemas/paper-journal-entry.json) | Persisted journal row / decision lifecycle state. |

All JSON Schema files use draft 2020-12 and are referenced from `openapi.yaml` via relative
`$ref` (e.g. `./schemas/ohlc-bar.json`) rather than duplicated inline, so the schema files stay
the single canonical definition for both API docs and any future contract-testing / codegen.

## Conventions carried over from theory/algorithm docs

- **Grades:** `A+ | A | B | C | F` (`CONFLUENCE-FRAMEWORK.md` §6.2). `F` = CONFLICT / news veto /
  midday new-entry → `NO_TRADE`.
- **Modes:** `R | C | T | NONE` (Reversal / Continuation / Time-watch / none).
- **Invalidation is mandatory:** `ConfluenceDecision.invalid_if` and `PaperJournalEntry.invalid_if`
  are schema-enforced non-empty whenever `mode != NONE` (design principle §2.4).
- **Reason codes:** `IctSnapshot.reasons` and `GannSnapshot.reasons` are closed enums matching
  `ICT-SIGNAL-ENGINE.md` §6 and `GANN-CYCLE-ENGINE.md` §6 exactly. `ConfluenceDecision.reasons`
  is intentionally an open string array (see open question #3 below).
- **`ny_time` is mandatory**, never optional, on every bar and every engine snapshot's `asof`
  basis — missing it must fail closed (`DATA_GAP`), per the P0 "session clock / DST" risk.
- **Weights versioning:** `ConfluenceDecision.weights_version` / `PaperJournalEntry.weights_version`
  exist so `confluence_weights_version` can be frozen and never silently changed (RISKS section,
  `GROK-DECISION-001`).
- **Auth:** every endpoint except `GET /api/health` and `GET /api/health/ny-time` requires a
  Bearer JWT validated against CSS JWKS with `clientId=trading-portal` (DEV IdP `:9000`,
  already `registered` per `agents/pre-work/02-architecture.md` §5 — contracts did not touch
  that registry).
- **`ny_time` self-check endpoint:** `GET /api/health/ny-time` is a separate, dedicated endpoint
  per architecture doc §7 (distinct from the general `/api/health`), because the DST correctness
  gate is a named MVP requirement (`GROK-DECISION-001` RISKS), not generic diagnostics.

## Open questions for the architect / engines hires

1. **Risk-deny / grade-F journal semantics.** `AUTOMATION-PIPELINE.md` §4 says "terminal states
   always write journal + metrics", but the Q1 promote gate says "CONFLICT / risk deny never
   creates paper entries". This contract models both as compatible: a `PaperJournalEntry` row
   *may* exist with `status=REJECTED` and `paper=null` (no position, no `entry_price`), i.e.
   "journal" ≠ "paper position". Please confirm this is the intended persistence behavior, or
   tell contracts to instead keep risk-denied/grade-F decisions out of the journal table
   entirely and rely on metrics/logs only.
2. **Gann `reasons` cycle-fraction and pivot labels.** `GANN-CYCLE-ENGINE.md` §6 lists `CYC_1_4 |
   CYC_1_2 | CYC_3_4 | ...` and `PIVOT_NY_OPEN | PIVOT_PDH | ...` with an ellipsis. This contract
   derived the full closed set from `cfg.cycle_fractions` / `pivot_source` (`CYC_1_8, CYC_1_4,
   CYC_1_3, CYC_1_2, CYC_3_4, CYC_7_8` and one `PIVOT_*` per pivot source). Please confirm naming
   or correct before the engines hire encodes these as string constants.
3. **`ConfluenceDecision.reasons` namespacing.** Worked example §9 shows engine-sourced reasons
   re-prefixed as `ICT_SWEEP_ALH` / `GANN_SO9` inside the decision object, which differs from the
   raw engine codes (`SWEEP_ALH`, `SO9_FINE`/`SO9_ODD`/`SO9_EVEN`) used in `IctSnapshot.reasons` /
   `GannSnapshot.reasons`. The schema leaves `ConfluenceDecision.reasons` as an open string array
   (documented convention: `ICT_<code>` / `GANN_<code>` passthrough + confluence-only codes like
   `ALIGN_LONG`, `CONFLICT`, `SOFT`) rather than a closed enum, to avoid hand-maintaining every
   `ICT_*`/`GANN_*` combination. Confirm this convention with the engines hire before coding.
4. **`GannSnapshot.so9.levels[].dist` sign convention.** Algorithm doc doesn't specify signed vs.
   absolute distance; schema allows either — pick one and update the schema description once
   decided (cosmetic, non-blocking).
5. **`ConfluenceDecision.entry.type` / `targets` cardinality.** No explicit minimum count is
   specified in the theory doc for Mode T ("time-triggered watch", not yet an entry plan), so
   `targets` is allowed to be an empty array only in that mode. Confirm this is acceptable for the
   Angular portal's "one reason + levels" viewport, or whether Mode T should omit `entry`/`stop`
   entirely (currently required on the object regardless of mode).
6. **Port in `servers:` block.** `openapi.yaml` hardcodes `http://localhost:3340` per
   GROK-DECISION-001's *proposed* block. Contracts is not reserving/binding this — architect should
   confirm final DEV port in `E:\MyAgent\workflow\ports\REGISTRY.md` and this file's `servers:`
   entry should be updated to match if it ever changes.

## Explicit non-scope (per hire brief)

- No Spring Boot / Angular implementation code.
- No changes to port, DB, or CSS registries (`E:\MyAgent\workflow\ports|db|css\*`).
- No live/micro-live execution surface (`ExecutionRequest`/`ExecutionResult` from
  `AUTOMATION-PIPELINE.md` §3 Stage F are intentionally **not** contracted yet — out of MVP).
