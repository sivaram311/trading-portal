# Automation Pipeline — Signal → Risk → (Future) Execution

**Status:** Architecture sketch (v0) — **documentation only**  
**Instrument:** XAUUSD first  
**Disclaimer:** No live execution in this phase. Pipeline must honor research → paper → micro-live → scale gates. No profitability claim.

---

## 1. Purpose

Describe the end-to-end flow from market data to an optional future execution adapter, so architects and operators share one map before any port/DB/code work.

**Out of scope now:** binding ports, credentials, broker APIs, production order routers.

---

## 2. High-level flow

```text
                    ┌─────────────────────┐
                    │  Market data ingest │  (MT5 / vendor → store)
                    └──────────┬──────────┘
                               ▼
              ┌────────────────┴────────────────┐
              ▼                                 ▼
     ICT Signal Engine                 Gann Cycle Engine
     (IctSnapshot)                     (GannSnapshot)
              └────────────────┬────────────────┘
                               ▼
                      Confluence Layer
                   (ConfluenceDecision)
                               ▼
                        Risk Gate
                  (size, limits, vetoes)
                               ▼
                     ┌─────────┴─────────┐
                     ▼                   ▼
               Paper Journal        Operator Confirm UI
                     │                   │
                     └─────────┬─────────┘
                               ▼
                    Execution Adapter (FUTURE)
                    (broker / MT5 bridge)
```

---

## 3. Stages

### Stage A — Ingest

| Concern | v0 intent |
|---------|-----------|
| Symbol | XAUUSD |
| TFs | M1/M5/M15/H1/H4/D1 as needed |
| Clock | Broker zone → `ny_time` (+ optional IST display) |
| Store | TBD by architecture (schema-per-env when reserved) |

**Gate:** No engine run without validated `ny_time`.

### Stage B — Engines (parallel)

- `ICT-SIGNAL-ENGINE` → `IctSnapshot`  
- `GANN-CYCLE-ENGINE` → `GannSnapshot`  

Both pure functions over data+config; side-effect free except metrics/logs.

### Stage C — Confluence

Implement scoring/mode rules from `docs/theory/CONFLUENCE-FRAMEWORK.md`.  
Output: `ConfluenceDecision` with `automation: allow|confirm|deny`.

### Stage D — Risk gate

Even when confluence says `allow`, risk may deny.

| Check | Example policy (tunable) |
|-------|--------------------------|
| Max risk per trade | ≤ 0.5% equity (paper tracked) |
| Max daily loss | halt new entries at −2R day |
| Max open positions | 1 on XAUUSD v1 |
| News veto | NFP/FOMC/CPI blackout windows |
| Spread / session | deny if spread > threshold or midday new-entry |
| Duplicate suppression | same direction within N minutes |
| Grade floor | live requires A+; paper allows A |

**Output:** `RiskVerdict { ok, size, deny_reasons[] }`

### Stage E — Disposition

| automation + risk | Action |
|-------------------|--------|
| deny / !ok | Journal only |
| confirm + ok | Portal prompt (human) |
| allow + ok | Paper auto-fill **or** (future) execution adapter |

### Stage F — Execution adapter (FUTURE)

Sketch only:

```text
ExecutionRequest {
  decision_id, direction, entry, stop, targets, size, client_tag
}
ExecutionResult {
  status: accepted|rejected|partial|error
  broker_ids[], fill_px, ts
}
```

Requirements when built: idempotent `decision_id`, kill switch, CSS-authenticated operator overrides, full audit trail.

---

## 4. State machine (decision lifecycle)

```text
DETECTED → SCORED → RISK_CHECKED →
  ├─ REJECTED
  ├─ ALERTED (await confirm)
  ├─ PAPER_OPEN → PAPER_CLOSED
  └─ (future) LIVE_OPEN → LIVE_CLOSED
```

Terminal states always write journal + metrics.

---

## 5. Paper trading loop (mandatory before live)

1. Engines + confluence run on live or replay data.  
2. Risk gate sizes a **virtual** position.  
3. Stops/targets managed on bar stream (or tick later).  
4. Journal stores decision object + outcome (R-multiple, MFE/MAE).  
5. Weekly review: expectancy by grade/mode/killzone.  

**Promotion rule (proposed):** no micro-live until ≥ N trades (TBD) with stable positive expectancy on A/A+ **and** drawdown bounds respected — exact N set in pre-work approval, not here.

---

## 6. Operator portal surfaces (product sketch)

| View | Job |
|------|-----|
| Live confluence | Grade, mode, reasons, levels |
| Engine detail | ICT / Gann panels (research) |
| Risk / kill switch | Halt, reduce size, veto news |
| Journal | Search by grade, mode, session |
| Replay | Bar replay for a `decision_id` |

Auth: **CSS only** when portal is built (machine standing order). Ports reserved before listen.

---

## 7. Observability

| Signal | Why |
|--------|-----|
| Engine latency | Stale snapshots dangerous |
| Data gap alarms | Missing bars / ny_time |
| Decision rate | Spam vs silence |
| Deny reason histogram | Tune gates |
| Paper PnL curve | Gate to live |

Redact secrets; never log broker passwords or tokens.

---

## 8. Configuration layers

```text
defaults.yaml          # safe research defaults
env.dev.overrides      # after port/DB reserved
strategy.weights       # confluence points (versioned)
risk.policy            # hard limits
feature.flags          # enable Mode C auto, etc.
```

Config changes that affect live risk require human GO (ops), even after code exists.

---

## 9. Explicit non-goals (v0)

- No port binds, no DB schema creation in this hire  
- No MT5 EA order sender  
- No multi-symbol portfolio  
- No guaranteed SL on broker without adapter proof  
- No bypass of confluence CONFLICT rules for “speed”

---

## 10. Mapping to future code modules (names only)

| Module | Responsibility |
|--------|----------------|
| `ingest.*` | candles + timezone enrich |
| `engine.ict` | IctSnapshot |
| `engine.gann` | GannSnapshot |
| `confluence` | ConfluenceDecision |
| `risk` | RiskVerdict |
| `journal` | persistence |
| `portal` | UI |
| `exec.adapter` | future |

Exact stack TBD by Technical Architect pre-work.

---

## 11. Assumptions & open questions

### Assumptions

- Single-symbol v1 simplifies risk.  
- Human confirm is default until paper proves grade calibration.  
- Kill switch is mandatory before any live adapter.

### Open questions

1. Tick vs bar management for stops on gold spikes.  
2. Partial TP policy (scale-out at T1).  
3. Whether `allow` ever ships without confirm in first live micro.  
4. Multi-account / prop-firm rule packs later.  
5. Dependency on CSS session for confirm actions (expected yes).

---

## 12. Related docs

| Doc | Role |
|-----|------|
| `docs/theory/CONFLUENCE-FRAMEWORK.md` | Decision rules |
| `docs/algorithms/ICT-SIGNAL-ENGINE.md` | ICT compute |
| `docs/algorithms/GANN-CYCLE-ENGINE.md` | Gann compute |
| `agents/pre-work/01-vision-walkthrough.md` | Product vision |
| `docs/reference/GROK-DEV.md` | Prior pipeline patterns (reference only) |
