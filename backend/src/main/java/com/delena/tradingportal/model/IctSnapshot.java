package com.delena.tradingportal.model;

import java.time.Instant;
import java.util.List;

/** Output of the ICT Signal Engine (docs/contracts/schemas/ict-snapshot.json). */
public record IctSnapshot(
        String symbol,
        Instant asof,
        String killzone,
        Htf htf,
        Structure structure,
        Liquidity liquidity,
        Zones zones,
        int quality,
        List<String> reasons,
        RawRefs rawRefs
) {
    public record Htf(
            double dealingLow,
            double dealingHigh,
            double eq,
            String premiumDiscount,
            String bias
    ) {
    }

    public record Swing(String type, double price, Instant ts, Integer index) {
    }

    public record Structure(
            List<Swing> swings,
            String event,
            String direction,
            boolean displacement
    ) {
    }

    public record Pool(String name, double price, String side) {
    }

    public record Liquidity(
            List<Pool> pools,
            String event,
            String sweptPool,
            boolean reclaim
    ) {
    }

    public record Zone(String type, String direction, double low, double high, String state, Instant ts) {
    }

    public record OteZone(double deep, double sweet, double shallow, double invalidation) {
    }

    public record Zones(
            List<Zone> orderBlocks,
            List<Zone> fvgs,
            Zone activeEntry,
            OteZone activeOte
    ) {
    }

    public record RawRefs(List<String> barIds) {
    }
}
