package com.delena.tradingportal.engine.ict;

/** ICT engine config defaults (ICT-SIGNAL-ENGINE.md §2.1 v1). */
public record IctConfig(
        int swingNM15,
        int swingNH1,
        double equalEpsPts,
        int sweepReclaimBars,
        double minFvgAtrFrac,
        double minFvgPts,
        double displacementBodyFrac,
        int atrPeriod
) {
    public static IctConfig defaults() {
        return new IctConfig(2, 3, 0.6, 3, 0.5, 0.8, 0.60, 14);
    }
}
