# Grok hire brief — Trading Portal theories & algorithms

**Hire id:** `trading-portal-grok-theory-2026-07-15`  
**Model:** `cursor-grok-4.5-high` via Cursor Agent CLI  
**Workspace:** `E:\MyWorkspace\trading-portal`  
**Mode:** Write documentation only (no app implementation, no port binds)

## Deliverables (create/overwrite these files)

1. `docs/theory/ICT-GOLD.md` — ICT concepts for XAUUSD (actionable, definition-grade)
2. `docs/theory/GANN-INTRADAY-TIME-CYCLES.md` — W.D. Gann for intraday + time cycles on gold
3. `docs/theory/CONFLUENCE-FRAMEWORK.md` — How ICT + Gann combine into decisions
4. `docs/algorithms/ICT-SIGNAL-ENGINE.md` — Computable steps, inputs/outputs, pseudocode
5. `docs/algorithms/GANN-CYCLE-ENGINE.md` — Computable Gann/cycle engine + pseudocode
6. `docs/algorithms/AUTOMATION-PIPELINE.md` — Signal → risk → (future) execution pipeline sketch
7. `agents/pre-work/01-vision-walkthrough.md` — First draft vision for the portal/product
8. Update `agents/crew-activity.md` with this hire result

## Constraints

- Instrument: **XAUUSD / gold** first.
- Reference ideas from `E:\Source\grok_dev` (Gann Intraday, NY liquidity) but write **new** SoT docs for this project.
- Be precise: sessions (Asian/London/NY), liquidity pools, FVG/imbalance, order blocks, MSS/BOS, premium/discount; Gann 1x1 angles, Square of 9, time=price squaring, cycle divisions.
- Mark assumptions and open research questions clearly.
- Do **not** claim guaranteed profitability; frame as research → paper → live gates.
- Do **not** reserve ports, invent credentials, or write production trading code yet.
- Do **not** delete anything.
- Follow existing README tone; keep docs operator/engineer usable.
