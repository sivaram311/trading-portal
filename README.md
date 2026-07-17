# Trading Portal

Automated trading **application + portal** for **XAUUSD (gold)**.

**Status:** Live on F/G at **`v0.3.2`** (paper + P5 fail-closed) · DEV on `E:\MyWorkspace`  
**Created:** 2026-07-15  
**Feature honesty:** `docs/FEATURE-VALIDATION-0.3.1.md` · `GET /api/ops/status` → `features`

## Scope (v0 / v1)

| Pillar | Focus |
|--------|--------|
| **ICT** | Inner Circle Trader concepts applied to gold — liquidity, structure, sessions, FOLB/FVG, killzones |
| **W.D. Gann** | Intraday angles, Square of 9, time cycles / squaring, session pivots |
| **Automation** | Signal engines → risk rules → (later) execution adapters |
| **Portal** | Operator UI for signals, confluence, journal, and controls |

Reference only (do **not** fork as source of truth): `E:\Source\grok_dev` — market grid, Gann Intraday, NY Liquidity Analyzer, MT5 Python pipeline.

## Layout

```
trading-portal/
├── agents/           # Crew + pre-work (personas gate)
├── docs/
│   ├── theory/       # ICT + Gann domain docs (Grok-authored)
│   ├── algorithms/   # Computable signal / cycle engines
│   └── reference/    # Pointers to grok_dev & MT5 lessons
├── backend/          # (pending architecture approval)
├── frontend/         # (pending architecture approval)
├── python/           # Market data / research scripts (later)
└── scripts/
```

## Machine rules

- Ports / DB / CSS: reserve in `E:\MyAgent\workflow\` **before** any listen or auth wiring.
- Auth for the portal: **CSS only** (`centralized-security-system`, DEV `:9000`).
- Pre-work under `agents/pre-work/` must be approved before application coding (CONSCIOUS + personas).

## Next

1. Grok theory + algorithm pack under `docs/theory/` and `docs/algorithms/`
2. Crew pre-work: vision → architecture → contracts → design → approval
3. Port + DB reservation, then scaffold backend/frontend
