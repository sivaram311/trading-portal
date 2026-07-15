package com.delena.tradingportal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
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

    public record Cycles(double sessionFraction, String checkpoint) {
    }

    public record Filters(boolean volumeSpike, boolean reversalCandle, Boolean rsiDiv) {
    }
}
