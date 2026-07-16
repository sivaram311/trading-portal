package com.delena.tradingportal.paper;

import com.delena.tradingportal.engine.style.StyleProfile;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.model.PaperJournalEntry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bar-by-bar paper position lifecycle: BE, partial at T1, ATR trail, exits.
 * Paper-only; no pyramiding ({@code maxLegs} = 1 on paper path).
 */
@Component
public class PositionManager {

    public static final double BE_BUFFER_R = 0.05;
    public static final double DEFAULT_TRAIL_ATR_MULT = 1.0;
    private static final int DEFAULT_ATR_PERIOD = 14;

    public record BarResult(
            PaperJournalEntry entry,
            String status,
            boolean closed
    ) {
    }

    /**
     * Apply one bar of management to an open paper journal entry.
     */
    public BarResult onBar(PaperJournalEntry entry, OhlcBar bar, StyleProfile style, double atr,
                           boolean structureFlip) {
        PaperJournalEntry.Paper paper = entry.paper();
        if (paper == null || paper.entryPrice() == null) {
            return new BarResult(entry, entry.status(), false);
        }

        double entryPrice = paper.entryPrice();
        double initialStop = entry.stop();
        double risk = Math.abs(entryPrice - initialStop);
        if (risk <= 0) {
            return new BarResult(entry, entry.status(), false);
        }

        boolean longDir = isLong(entry.direction());
        double currentStop = paper.currentStop() != null ? paper.currentStop() : initialStop;
        double remaining = paper.remainingSize() != null ? paper.remainingSize() : 1.0;
        boolean beActive = Boolean.TRUE.equals(paper.beActive());
        boolean t1Hit = Boolean.TRUE.equals(paper.t1Hit());
        boolean wasT1Hit = t1Hit;

        double mfeR = paper.mfeR() != null ? paper.mfeR() : 0.0;
        double maeR = paper.maeR() != null ? paper.maeR() : 0.0;
        mfeR = Math.max(mfeR, favorableR(longDir, entryPrice, risk, longDir ? bar.high() : bar.low()));
        maeR = Math.max(maeR, adverseR(longDir, entryPrice, risk, longDir ? bar.low() : bar.high()));

        double scaleOutPct = style.scaleOutPct();
        double beTriggerR = style.beTriggerR();
        Double t1 = firstTarget(entry);
        Double t2 = secondTarget(entry);

        // Structure flip — force close at bar close.
        if (structureFlip) {
            return close(entry, paper, bar.ts(), bar.close(), "STRUCTURE_FLIP", currentStop, remaining,
                    beActive, t1Hit, mfeR, maeR, entryPrice, risk, longDir);
        }

        // Max hold exceeded.
        if (paper.openedAt() != null && style.maxHold() != null) {
            Duration held = Duration.between(paper.openedAt(), bar.ts());
            if (!held.isNegative() && held.compareTo(style.maxHold()) > 0) {
                return close(entry, paper, bar.ts(), bar.close(), "TIME", currentStop, remaining,
                        beActive, t1Hit, mfeR, maeR, entryPrice, risk, longDir);
            }
        }

        // Break-even after +beTriggerR (use favorable excursion on this bar).
        double favorableExtreme = longDir ? bar.high() : bar.low();
        double excursionR = favorableR(longDir, entryPrice, risk, favorableExtreme);
        if (!beActive && excursionR >= beTriggerR) {
            currentStop = beStop(longDir, entryPrice, risk);
            beActive = true;
        }

        // Stop hit (before new targets on same bar — conservative).
        if (stopHit(longDir, bar, currentStop)) {
            String reason = beActive && isBeStop(longDir, entryPrice, risk, currentStop) ? "BE_STOP" : "STOP";
            double exitPx = currentStop;
            return close(entry, paper, bar.ts(), exitPx, reason, currentStop, remaining,
                    beActive, t1Hit, mfeR, maeR, entryPrice, risk, longDir);
        }

        // T1 scale-out.
        if (!t1Hit && t1 != null && targetTouched(longDir, bar, t1)) {
            t1Hit = true;
            remaining = round(Math.max(0.0, remaining - scaleOutPct));
            if (!beActive) {
                currentStop = beStop(longDir, entryPrice, risk);
                beActive = true;
            }
            if (remaining <= 0.0) {
                return close(entry, paper, bar.ts(), t1, "T1", currentStop, 0.0,
                        beActive, true, mfeR, maeR, entryPrice, risk, longDir);
            }
        }

        // ATR trail on remainder after T1 (from the bar after T1 is first hit).
        if (wasT1Hit && t1Hit && remaining > 0 && atr > 0) {
            currentStop = trailStop(longDir, currentStop, bar, atr, DEFAULT_TRAIL_ATR_MULT);
        }

        // T2 on remainder.
        if (t1Hit && t2 != null && remaining > 0 && targetTouched(longDir, bar, t2)) {
            return close(entry, paper, bar.ts(), t2, "T2", currentStop, remaining,
                    beActive, t1Hit, mfeR, maeR, entryPrice, risk, longDir);
        }

        // Re-check trail stop after ratchet (not on the T1 fill bar).
        if (wasT1Hit && t1Hit && stopHit(longDir, bar, currentStop)) {
            String reason = beActive && isBeStop(longDir, entryPrice, risk, currentStop) ? "BE_STOP" : "STOP";
            return close(entry, paper, bar.ts(), currentStop, reason, currentStop, remaining,
                    beActive, t1Hit, mfeR, maeR, entryPrice, risk, longDir);
        }

        var updatedPaper = new PaperJournalEntry.Paper(
                paper.openedAt(), null, entryPrice, null, null, null, mfeR, maeR,
                round(currentStop), round(remaining), beActive, t1Hit);
        String status = t1Hit && remaining > 0 && remaining < 1.0 ? "PARTIAL" : entry.status();
        if (!"PARTIAL".equals(status) && !"PAPER_OPEN".equals(status)) {
            status = "PAPER_OPEN";
        }
        return new BarResult(rebuild(entry, status, updatedPaper), status, false);
    }

    /**
     * Force-close at a price (operator manual exit).
     */
    public BarResult closeAt(PaperJournalEntry entry, Instant when, double exitPrice, String exitReason) {
        PaperJournalEntry.Paper paper = entry.paper();
        if (paper == null || paper.entryPrice() == null) {
            return new BarResult(entry, entry.status(), false);
        }
        double entryPrice = paper.entryPrice();
        double risk = Math.abs(entryPrice - entry.stop());
        if (risk <= 0) {
            return new BarResult(entry, entry.status(), false);
        }
        boolean longDir = isLong(entry.direction());
        double remaining = paper.remainingSize() != null ? paper.remainingSize() : 1.0;
        double currentStop = paper.currentStop() != null ? paper.currentStop() : entry.stop();
        return close(entry, paper, when, exitPrice, exitReason, currentStop, remaining,
                Boolean.TRUE.equals(paper.beActive()), Boolean.TRUE.equals(paper.t1Hit()),
                paper.mfeR() != null ? paper.mfeR() : 0.0,
                paper.maeR() != null ? paper.maeR() : 0.0,
                entryPrice, risk, longDir);
    }

    public static double atr(List<OhlcBar> bars, int period) {
        if (bars == null || bars.size() < 2) {
            return 0;
        }
        int n = Math.min(period, bars.size() - 1);
        double sum = 0;
        for (int i = bars.size() - n; i < bars.size(); i++) {
            OhlcBar cur = bars.get(i);
            OhlcBar prev = bars.get(i - 1);
            double tr = Math.max(cur.high() - cur.low(),
                    Math.max(Math.abs(cur.high() - prev.close()), Math.abs(cur.low() - prev.close())));
            sum += tr;
        }
        return sum / n;
    }

    public static double atr(List<OhlcBar> bars) {
        return atr(bars, DEFAULT_ATR_PERIOD);
    }

    // ------------------------------------------------------------------ internals

    private BarResult close(PaperJournalEntry entry, PaperJournalEntry.Paper paper, Instant when,
                            double exitPrice, String exitReason, double currentStop, double remaining,
                            boolean beActive, boolean t1Hit, double mfeR, double maeR,
                            double entryPrice, double risk, boolean longDir) {
        Double t1 = firstTarget(entry);
        double rMultiple = realizedR(longDir, entryPrice, risk, t1Hit, t1, remaining, exitPrice);
        var closedPaper = new PaperJournalEntry.Paper(
                paper.openedAt(), when, entryPrice, round(exitPrice), exitReason, round(rMultiple),
                mfeR, maeR, round(currentStop), round(remaining), beActive, t1Hit);
        return new BarResult(rebuild(entry, "PAPER_CLOSED", closedPaper), "PAPER_CLOSED", true);
    }

    static double realizedR(boolean longDir, double entryPrice, double risk,
                          boolean t1Hit, Double t1, double remaining, double exitPrice) {
        double total = 0;
        if (t1Hit && t1 != null) {
            double scaledOut = 1.0 - remaining;
            if (scaledOut > 0) {
                total += scaledOut * priceR(longDir, entryPrice, risk, t1);
            }
        }
        if (remaining > 0) {
            total += remaining * priceR(longDir, entryPrice, risk, exitPrice);
        }
        return total;
    }

    static double priceR(boolean longDir, double entryPrice, double risk, double price) {
        if (longDir) {
            return (price - entryPrice) / risk;
        }
        return (entryPrice - price) / risk;
    }

    static double favorableR(boolean longDir, double entryPrice, double risk, double price) {
        return Math.max(0, priceR(longDir, entryPrice, risk, price));
    }

    static double adverseR(boolean longDir, double entryPrice, double risk, double price) {
        return Math.max(0, -priceR(longDir, entryPrice, risk, price));
    }

    static double beStop(boolean longDir, double entryPrice, double risk) {
        double buffer = BE_BUFFER_R * risk;
        return longDir ? entryPrice + buffer : entryPrice - buffer;
    }

    static boolean isBeStop(boolean longDir, double entryPrice, double risk, double stop) {
        double be = beStop(longDir, entryPrice, risk);
        return Math.abs(stop - be) < risk * 0.001;
    }

    static double trailStop(boolean longDir, double currentStop, OhlcBar bar, double atr, double mult) {
        if (longDir) {
            double candidate = bar.high() - mult * atr;
            return Math.max(currentStop, candidate);
        }
        double candidate = bar.low() + mult * atr;
        return Math.min(currentStop, candidate);
    }

    static boolean stopHit(boolean longDir, OhlcBar bar, double stop) {
        return longDir ? bar.low() <= stop : bar.high() >= stop;
    }

    static boolean targetTouched(boolean longDir, OhlcBar bar, double target) {
        return longDir ? bar.high() >= target : bar.low() <= target;
    }

    static Double firstTarget(PaperJournalEntry entry) {
        if (entry.targets() == null || entry.targets().isEmpty()) {
            return null;
        }
        return entry.targets().getFirst();
    }

    static Double secondTarget(PaperJournalEntry entry) {
        if (entry.targets() == null || entry.targets().size() < 2) {
            return null;
        }
        return entry.targets().get(1);
    }

    static boolean isLong(String direction) {
        return "long".equalsIgnoreCase(direction);
    }

    static PaperJournalEntry rebuild(PaperJournalEntry entry, String status, PaperJournalEntry.Paper paper) {
        return new PaperJournalEntry(entry.id(), entry.decisionId(), entry.symbol(), entry.sessionDate(), status,
                entry.mode(), entry.direction(), entry.grade(), entry.score(), entry.reasons(),
                entry.weightsVersion(), entry.entry(), entry.stop(), entry.targets(), entry.invalidIf(),
                entry.automation(), entry.risk(), entry.detectedAt(), entry.actionedAt(), entry.actionedBy(),
                entry.actionNote(), paper);
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
