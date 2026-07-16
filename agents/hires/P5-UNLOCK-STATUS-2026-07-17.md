# P5 unlock status — 2026-07-17

User said **"proceed"** after HOLD list (P5 · F/G promote · DXY SMT).

| Item | Interpretation |
|------|----------------|
| DXY SMT | **Implement** (done) |
| F/G promote | **Paper-only 0.3** (`live-enabled=false`) — proceed with tag + Q1/Q2 evidence |
| P5 micro-live | **Still HOLD** — `P5-MICRO-LIVE-HOLD.md` requires exact sentence: `GO micro-live P5 on DEV only` (or PREPROD). Plain "proceed" is not sufficient for broker `order_send`. |

No `trading.exec.live-enabled=true` on any env in this wave.
