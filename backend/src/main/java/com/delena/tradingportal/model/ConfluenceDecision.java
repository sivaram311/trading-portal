package com.delena.tradingportal.model;

import java.time.Instant;
import java.util.List;

/** Single handoff decision object (docs/contracts/schemas/confluence-decision.json). */
public record ConfluenceDecision(
        String id,
        String symbol,
        Instant ts,
        String mode,
        String direction,
        String grade,
        double score,
        String agreement,
        List<String> reasons,
        Entry entry,
        double stop,
        List<Double> targets,
        List<String> invalidIf,
        Engines engines,
        String automation,
        String weightsVersion
) {
    public record Engines(String ictRef, String gannRef) {
    }
}
