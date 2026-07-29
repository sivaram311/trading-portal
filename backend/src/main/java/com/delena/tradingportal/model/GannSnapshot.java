package com.delena.tradingportal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Output of the Gann Cycle Engine (docs/contracts/schemas/gann-snapshot.json). */
public record GannSnapshot(
        String symbol,
        Instant asof,
        Pivot pivot,
        Angle angle,
        So9 so9,
        TimeSquare timeSquare,
        Cycles cycles,
        String killzone,
        String gannBias,
        int quality,
        List<String> reasons,
        Filters filters
) {
    public record Pivot(String source, double price, Instant originNy) {
    }

    public record Fan(double m1x1, double m2x1, double m1x2) {
    }

    public record Angle(
            @JsonProperty("slope_1x1") double slope1x1,
            double equilibrium,
            double deviation,
            double stretchAtr,
            String bias,
            boolean alert,
            Fan fan
    ) {
    }

    public record So9Level(String kind, double k, double price, double dist) {
    }

    public record So9(List<So9Level> levels, boolean atLevel, So9Level nearest) {
    }

    public record Milestone(double m, double target, boolean nearTime, boolean nearPrice, boolean nearSquare) {
    }

    public record TimeSquare(
            double minutesElapsed,
            double priceMove,
            List<Milestone> milestones,
            boolean anyNearSquare
    ) {
    }

    /**
     * Multi-day swing-cycle overlay (GANN-CYCLE-ENGINE.md §7 — observe-only). {@code
     * multiDayOrigin}/{@code multiDayCheckpoints}/{@code multiDayLabel} are additive and default
     * to {@code null}/empty when D1 history is insufficient; {@code sessionFraction} and {@code
     * checkpoint} (intraday session-cycle fields) are unchanged.
     */
    public record Cycles(
            double sessionFraction,
            String checkpoint,
            MultiDayOrigin multiDayOrigin,
            List<MultiDayCheckpoint> multiDayCheckpoints,
            String multiDayLabel
    ) {
        /** Backward-compatible constructor for existing callers that only set the intraday fields. */
        public Cycles(double sessionFraction, String checkpoint) {
            this(sessionFraction, checkpoint, null, List.of(), null);
        }
    }

    public record MultiDayOrigin(double price, LocalDate date, String kind) {
    }

    /** One projected day-count checkpoint (e.g. 3/7/14/21 trading days from the swing origin). */
    public record MultiDayCheckpoint(int dayCount, LocalDate date, boolean activeToday, boolean near) {
    }

    public record Filters(boolean volumeSpike, boolean reversalCandle, Boolean rsiDiv) {
    }
}
