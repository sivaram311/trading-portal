package com.delena.tradingportal.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Persisted decision-lifecycle record (docs/contracts/schemas/paper-journal-entry.json). */
public record PaperJournalEntry(
        String id,
        String decisionId,
        String symbol,
        LocalDate sessionDate,
        String status,
        String mode,
        String direction,
        String grade,
        double score,
        List<String> reasons,
        String weightsVersion,
        Entry entry,
        double stop,
        List<Double> targets,
        List<String> invalidIf,
        String automation,
        RiskSummary risk,
        Instant detectedAt,
        Instant actionedAt,
        String actionedBy,
        String actionNote,
        Paper paper
) {
    public record RiskSummary(boolean ok, Double size, List<String> denyReasons) {
    }

    public record Paper(
            Instant openedAt,
            Instant closedAt,
            Double entryPrice,
            Double exitPrice,
            String exitReason,
            Double rMultiple,
            Double mfeR,
            Double maeR,
            /** Active stop (initially decision stop; may move to BE / trail). */
            Double currentStop,
            /** Remaining size fraction after T1 scale-out (1.0 = full size). */
            Double remainingSize,
            Boolean beActive,
            Boolean t1Hit
    ) {
    }
}
