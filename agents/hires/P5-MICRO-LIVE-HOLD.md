# P5 Micro-live — HOLD (design only)

**Status:** HOLD — no implementation that places broker orders  
**Decision maker:** User must give explicit GO before any adapter work  
**Prerequisite (DECISION-001):** PREPROD paper soak ≥30 journaled decisions **or** ≥10 trading days  

## Intent (when unlocked)

- Micro-size live adapter behind `trading.exec.live-enabled` (default **false**)
- Hard kill switch (config + OPS endpoint + process stop)
- Same RiskGate / CONFLICT / NEWS_VETO fail-closed path as paper
- Max 1 open; size floor at micro lot; daily −2R halt
- Full journal of LIVE_* actions separate from PAPER_*

## Forbidden until user GO

- Calling MT5 `order_send` / any broker REST
- Setting `trading.exec.live-enabled=true` in F: or G:
- Shipping a “dry-run live” that can accidentally become live

## Design sketch (non-binding)

```
Pipeline → RiskGate ok → PaperAutoConfirm? → (future) LiveAdapter.execute(micro)
                                              ↓
                                         killSwitch || !liveEnabled → no-op
```

## Ask user when ready

1. Confirm soak metrics meet threshold on PREPROD  
2. Explicit sentence: “GO micro-live P5 on DEV only” (or PREPROD)  
3. Confirm kill-switch owner / on-call  

Until then this file is the SoT that P5 is **not** in scope for coding.
