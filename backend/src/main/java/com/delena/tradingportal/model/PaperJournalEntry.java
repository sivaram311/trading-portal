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
        Boolean t1Hit,
        /**
         * Number of legs currently filled on this position (1 = initial only; paper-path
         * pyramiding — DEEP-ALGORITHMS §7). Null is treated as 1 for entries written before
         * ADD_LEG existed.
         */
        Integer legs,
        /** Legs added on top of the initial fill via ADD_LEG (limited pyramiding), oldest first. */
        List<AddLeg> addLegs
    ) {
        /** Number of legs open, defaulting missing/legacy data to 1 (initial leg only). */
        public int legCount() {
            if (legs != null) {
                return legs;
            }
            return addLegs != null ? 1 + addLegs.size() : 1;
        }

        /**
         * One ADD_LEG fill (paper pyramiding). {@code stopAtAdd} is the shared position stop at
         * the moment the leg was added, used to compute this leg's own R contribution at close
         * since it usually differs from the initial leg's risk (stop has typically moved to BE
         * or better by the time {@code unrealizedR >= 0.8}).
         */
        public record AddLeg(
                Instant addedAt,
                double entryPrice,
                double stopAtAdd,
                /** Fraction of the original leg's size/risk this add represents (0-1). */
                double sizeFraction,
                /** Risk (% of equity) this add contributes, informational/audit only. */
                double riskPctAdded
        ) {
        }
    }
}
