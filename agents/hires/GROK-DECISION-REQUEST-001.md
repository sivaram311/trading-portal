# DECISION REQUEST — Trading Portal (Grok is sole decision maker)

**Session:** `trading-portal-build-2026-07-15`  
**Requester:** Crew Lead (Cursor Auto / Composer)  
**Your role:** **Decision maker**. Lead will not invent stack/ports/scope without your GO. After each major milestone Lead will re-ask you until you say **GOOD TO PROMOTE** (PREPROD then PROD) or **STOP**.

## Machine constraints (non-negotiable — you must plan inside these)

From `E:\MyAgent\workflow\CONSCIOUS.md`:

- Ports: reserve before bind. DEV 3000–3999, PREPROD 4000–4999, PROD 5000–5999. SoT `workflow/ports/`.
- Postgres schema-per-env on shared `:5432`.
- Auth: CSS only (`centralized-security-system` DEV `:9000`) — register `clientId`.
- Promote F/G only with evidence packs under `H:\releases\...` + EM roles — your “GOOD TO PROMOTE” unlocks Lead hiring promote crew; Lead will still produce CHECKLIST/SUMMARY evidence.
- No deletes without user confirm.
- Pre-work normally gates coding; **you may waive or compress** pre-work if you explicitly state APPROVE_CODING=GO and list which docs must exist.

## Current state

- Project root: `E:\MyWorkspace\trading-portal`
- Theory + algorithms + vision draft already written (Grok hire earlier today)
- No backend/frontend code yet; empty `backend/`, `frontend/`, `python/`
- Reference: `E:\Source\grok_dev` (Spring Boot + Angular + MT5 Python + Gann/NY liquidity tools) — reference only
- Hub already has `mt5-dev` nearby

## User order

> Proceed start to implement them all. Hire subagents with required skills and architect, follow the workflow then review with grok, then follow grok recommendation then again get the grok recommendation. Proceed until grok says good to promote to preprod and prod. Now grok only will act as your decision maker. Start asking with grok.

## Your decision output (write ALL of this into `agents/hires/GROK-DECISION-001.md`)

Use this exact structure:

```
## VERDICT
APPROVE_CODING: GO | NO-GO
REASON: ...

## MVP SCOPE (must be implementable on DEV this session wave)
- include: ...
- exclude (explicit): ...

## STACK DECISION
backend: ...
frontend: ...
engines: ...
data: ...
auth: CSS (yes/how)

## PORTS (propose free set; Lead will reserve)
DEV api/ui/worker: ...
PREPROD: ...
PROD: ...

## DB
app id / database name / schemas: ...

## PHASE PLAN (ordered)
P0: ...
P1: ...
...
Promote gate criteria (what must be true for you to say GOOD TO PROMOTE Q1/Q2)

## HIRES (order Lead must launch)
1. role | mission | done-when
2. ...

## FIRST ACTIONS FOR LEAD (checklist)
- [ ] ...

## RISKS / HOLDS
...
```

Also update `agents/crew-activity.md`.

Read existing docs under `docs/theory/`, `docs/algorithms/`, `agents/pre-work/01-vision-walkthrough.md`, and skim `E:\Source\grok_dev\README.md` before deciding.

Be decisive. Prefer a thin vertical slice that proves ICT+Gann confluence + paper pipeline + portal UI over boiling the ocean. Live broker execution stays gated unless you explicitly include micro-paper only for MVP.
