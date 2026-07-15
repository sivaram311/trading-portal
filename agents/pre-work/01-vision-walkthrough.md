# Pre-work 01 — Vision walkthrough (Trading Portal)

**Status:** First draft — awaiting crew / EM review  
**Project:** `E:\MyWorkspace\trading-portal`  
**Date:** 2026-07-15  
**Author role:** Quant/Strategy SME (Grok hire) + Vision seed  
**Gate:** No application coding until later pre-work ends in `agents/pre-work/approval.md` **GO**.

---

## 1. One-sentence vision

**Trading Portal** is an operator-facing application that turns **ICT structure/liquidity** and **W.D. Gann intraday geometry & time cycles** into **explainable confluence decisions** for **XAUUSD**, with paper-first automation and a future path to gated live execution.

---

## 2. Problem

Today, gold discretionary traders (and prior tooling such as `grok_dev`) often have:

- Strong **single-lens** views (Gann dashboard **or** liquidity sweep **or** raw charts)
- Weak **fusion** into one graded decision with mandatory invalidation
- No shared **SoT** for ICT+Gann definitions tied to an automation pipeline
- Risk of jumping to execution before expectancy is measured

Operators need one portal that answers: *What is the setup, why, what invalidates it, and may we risk paper/live size?*

---

## 3. Product pillars

| Pillar | User value |
|--------|------------|
| **Theory SoT** | Shared language (docs already seeded under `docs/theory/`) |
| **Engines** | Computable ICT + Gann snapshots |
| **Confluence** | Single graded decision object |
| **Journal** | Learning loop (grade → outcome) |
| **Risk** | Hard stops on behavior before broker |
| **Portal UI** | Monitor, confirm, halt — phone + desktop |
| **Execution (later)** | Adapter behind kill switch |

---

## 4. Primary users

| User | Need |
|------|------|
| Solo / desk operator | See A+/A setups in killzones; confirm or skip |
| Strategy researcher | Replay, tune weights, label history |
| Ops / EM | Gates, promote evidence, no silent live flips |

**Non-user (v1):** public retail SaaS multi-tenant; social copy-trading.

---

## 5. Experience sketch (first viewport later)

When UI work begins (design system TBD), the **live confluence** view should read as one job:

1. **Brand / product** — Trading Portal  
2. **Headline** — grade + direction + mode (e.g. A+ SHORT · Mode R)  
3. **One supporting line** — top reason (sweep + stretch + killzone)  
4. **CTA** — Confirm paper / Dismiss / Open journal  
5. **Dominant visual** — price context with levels (liquidity, 1×1, So9, entry/stop)

Avoid dashboard clutter in the first viewport (stats strips, multi-card walls). Detail panels scroll below.

**Devices (machine SoT):** Realme **360×780** primary; desktop **1280×800**; tablet **800×1280** for E2E when UI exists.

---

## 6. Success metrics (proposed)

| Metric | Research / paper target |
|--------|-------------------------|
| Decision explainability | 100% of alerts have reason codes |
| Grade calibration | A+/A expectancy > B over sample N (N TBD) |
| Conflict handling | Zero auto entries on CONFLICT |
| Risk adherence | No paper day breaches of daily loss policy |
| Operator time | < 30s to accept/reject an alert |

Live metrics only after micro-live GO.

---

## 7. Scope boundaries

### In scope (near term)

- Docs → architecture → contracts → design → approval  
- Data ingest design for XAUUSD  
- Engines + confluence + paper journal  
- CSS-authenticated portal  

### Out of scope (v1)

- Multi-asset portfolio optimization  
- Guaranteed profit / signal-sale marketing  
- Blind copy of grok_dev ports/schemas  
- Live order routing before paper gates  

---

## 8. Relationship to grok_dev

`E:\Source\grok_dev` is **reference only** (Gann Intraday, NY liquidity, MT5→DB patterns, timezone handling).  

Trading Portal is a **new** app: own ports, DB, CSS `clientId`, docs, and confluence-first product — not a rename/fork SoT.

---

## 9. Delivery phases (vision-level)

| Phase | Outcome |
|-------|---------|
| **P0** | Theory + algorithms (this hire) ✅ |
| **P1** | Architecture + contracts + design + approval GO |
| **P2** | Ingest + engines + confluence + paper journal (DEV) |
| **P3** | Portal UI + CSS auth + Device Lab E2E |
| **P4** | Micro-live adapter behind kill switch (explicit GO) |

---

## 10. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Overfit confluence weights | Walk-forward; freeze versions; journal honesty |
| Session clock bugs | Mandatory `ny_time` tests; DST cases |
| Automation urge before proof | Paper gate + EM GO culture |
| Confusing fade vs trend | Mode R vs Mode C explicit in UI |
| Gold news spikes | Calendar veto + spread checks |

---

## 11. Open decisions for next pre-work

1. Stack choice (e.g. whether to resemble grok_dev Spring/Angular or not)  
2. DB/port reservation ids  
3. CSS `clientId` name  
4. Paper sample size N before micro-live  
5. Confirm-always vs allow-auto for A+ only  

---

## 12. Recommendation

Accept this vision as the **P0 north star**. Next hire: Technical Architect for stack/topology, then design + contracts. **Do not** implement backend/frontend until `approval.md` is GO.

---

## 13. References (in-repo)

- `docs/theory/ICT-GOLD.md`  
- `docs/theory/GANN-INTRADAY-TIME-CYCLES.md`  
- `docs/theory/CONFLUENCE-FRAMEWORK.md`  
- `docs/algorithms/ICT-SIGNAL-ENGINE.md`  
- `docs/algorithms/GANN-CYCLE-ENGINE.md`  
- `docs/algorithms/AUTOMATION-PIPELINE.md`  
- `docs/reference/GROK-DEV.md`  
- `README.md`  
- `agents/crew-manifest.md`
