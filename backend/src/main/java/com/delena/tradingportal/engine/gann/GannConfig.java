package com.delena.tradingportal.engine.gann;

import java.util.List;

/** Gann engine config defaults (GANN-CYCLE-ENGINE.md §2.1). */
public record GannConfig(
        int atrPeriod,
        double atrAlert,
        double timeScale,
        int nearTimeMin,
        double so9NearPct,
        double so9NearPts,
        List<Double> so9FineSteps,
        int so9OddNMax,
        List<Integer> milestonesMin,
        int sessionLenMin,
        List<Double> cycleFractions,
        double volSpikeMult,
        int entryTfMinutes
) {
    public static GannConfig defaults() {
        return new GannConfig(
                14, 1.25, 1.0, 5, 0.0008, 0.5,
                List.of(0.25, 0.5, 1.0), 4,
                List.of(45, 90, 180), 540,
                List.of(0.125, 0.25, 0.333, 0.5, 0.75, 0.875), 1.8, 5);
    }
}
