package com.delena.tradingportal.engine.confluence;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.Entry;
import com.delena.tradingportal.model.GannSnapshot;
import com.delena.tradingportal.model.IctSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Confluence layer (docs/theory/CONFLUENCE-FRAMEWORK.md). Fuses an {@link IctSnapshot} and a
 * {@link GannSnapshot} into a single graded {@link ConfluenceDecision} with a direction-agreement
 * matrix, narrative mode (R/C/T/NONE), grade (A+/A/B/C/F), stable reason codes, mandatory
 * invalidation, and an automation policy. Fail-closed: CONFLICT / news veto / data gap → NONE/F/deny.
 */
@Component
public class ConfluenceEngine {

    public ConfluenceDecision decide(IctSnapshot ict, GannSnapshot gann, Instant ts,
                                     boolean newsVeto, String weightsVersion) {
        String ictDir = ictDirection(ict);
        String gannSide = gannSide(gann);
        boolean dataGap = ict.reasons().contains("DATA_GAP") || gann.reasons().contains("DATA_GAP");

        String agreement = agreement(ictDir, gannSide, ict.quality(), gann.quality(), dataGap);
        boolean midday = NyTime.isMidday(ts);

        Set<String> reasons = new LinkedHashSet<>();
        ict.reasons().forEach(r -> reasons.add("ICT_" + r));
        gann.reasons().forEach(r -> reasons.add("GANN_" + r));
        reasons.add(agreement);
        if (newsVeto) {
            reasons.add("NEWS_VETO");
        }

        var engines = new ConfluenceDecision.Engines(ict.asof().toString(), gann.asof().toString());

        // Fail-closed states -> mode NONE, grade F, deny.
        if (dataGap || newsVeto || "CONFLICT".equals(agreement)) {
            return new ConfluenceDecision(UUID.randomUUID().toString(), "XAUUSD", ts, "NONE", "flat",
                    "F", 0.0, agreement, new ArrayList<>(reasons), new Entry("NONE", 0, 0), 0.0,
                    List.of(), List.of(), engines, "deny", weightsVersion);
        }

        String direction = switch (agreement) {
            case "ALIGN_LONG" -> "long";
            case "ALIGN_SHORT" -> "short";
            default -> ictDir.equals("neutral") ? "flat" : ictDir;
        };

        boolean ictSweep = "sweep".equals(ict.liquidity().event()) && ict.liquidity().reclaim();
        boolean ictMss = "MSS".equals(ict.structure().event()) && ict.structure().direction().equals(direction);
        boolean ictBos = "BOS".equals(ict.structure().event()) && ict.structure().direction().equals(direction);
        boolean killzone = ict.killzone() != null || gann.killzone() != null;

        int gannConds = 0;
        if (gann.so9().atLevel()) {
            gannConds++;
        }
        if (gann.timeSquare().anyNearSquare()) {
            gannConds++;
        }
        if (gann.angle().alert()) {
            gannConds++;
        }
        boolean gannTrend = gann.gannBias().startsWith("trend_") || "balanced".equals(gann.angle().bias());
        boolean nearSquareOrCycle = gann.timeSquare().anyNearSquare() || gann.cycles().checkpoint() != null;
        int maxQuality = Math.max(ict.quality(), gann.quality());

        // --- Mode selection ---
        String mode;
        boolean aligned = agreement.startsWith("ALIGN_");
        if (aligned && ictSweep && ictMss && killzone && gannConds >= 2) {
            mode = "R";
        } else if (aligned && ictBos && killzone && gannTrend) {
            mode = "C";
        } else if (nearSquareOrCycle && maxQuality >= 3) {
            mode = "T";
        } else if (aligned && maxQuality >= 3) {
            mode = "C";
        } else {
            mode = "NONE";
        }

        // --- Score (§6.1) ---
        double score = 0;
        String pd = ict.htf().premiumDiscount();
        boolean pdAlign = ("short".equals(direction) && "premium".equals(pd))
                || ("long".equals(direction) && "discount".equals(pd));
        if (ictSweep) {
            score += 2;
        }
        if (ictMss) {
            score += 2;
        }
        if (ict.zones().activeEntry() != null) {
            score += 1;
        }
        if (pdAlign) {
            score += 1;
        }
        if ("R".equals(mode) && gann.angle().alert()) {
            score += 2;
        }
        if ("C".equals(mode) && gannTrend) {
            score += 2;
        }
        if (gann.so9().atLevel()) {
            score += 1;
        }
        if (gann.timeSquare().anyNearSquare()) {
            score += 1;
        }
        if (killzone) {
            score += 1;
        }
        if (gann.filters() != null && (gann.filters().volumeSpike() || gann.filters().reversalCandle())) {
            score += 1;
        }

        // --- Grade (§6.2) + soft penalties (§6.3) ---
        String grade = grade(score, aligned);
        if (midday && betterThan(grade, "B")) {
            grade = "B";
            reasons.add("MIDDAY_CAP");
        }
        if (!aligned && betterThan(grade, "B")) {
            grade = "B";
        }

        // Non-actionable grades collapse to a watch/none state.
        if (("C".equals(grade) || "B".equals(grade)) && "NONE".equals(mode) && nearSquareOrCycle && maxQuality >= 3) {
            mode = "T";
        }
        if (grade.equals("F")) {
            mode = "NONE";
            direction = "flat";
        }

        // --- Levels ---
        Entry entry = entry(ict, gann, direction);
        double stop = stop(ict, gann, direction, entry);
        List<Double> targets = targets(ict, gann, direction, mode);
        List<String> invalidIf = invalidations(direction, stop, mode);
        if ("NONE".equals(mode)) {
            invalidIf = List.of();
        }

        // --- Automation policy ---
        String automation = automation(grade, aligned);

        return new ConfluenceDecision(UUID.randomUUID().toString(), "XAUUSD", ts, mode, direction,
                grade, round(score), agreement, new ArrayList<>(reasons), entry, round(stop),
                targets, invalidIf, engines, automation, weightsVersion);
    }

    // ------------------------------------------------------------------ helpers

    private static String ictDirection(IctSnapshot ict) {
        String d = ict.structure().direction();
        if (!"none".equals(d)) {
            return d;
        }
        String bias = ict.htf().bias();
        return "neutral".equals(bias) ? "neutral" : bias;
    }

    private static String gannSide(GannSnapshot gann) {
        return switch (gann.gannBias()) {
            case "fade_long", "trend_long" -> "long";
            case "fade_short", "trend_short" -> "short";
            default -> "neutral";
        };
    }

    private static String agreement(String ictDir, String gannSide, int ictQ, int gannQ, boolean dataGap) {
        if (dataGap || ictQ == 0) {
            return "NONE";
        }
        if ("neutral".equals(ictDir) || "neutral".equals(gannSide)) {
            // SOFT: allowed only under extra conditions handled in mode/grade logic.
            if ("neutral".equals(ictDir) && "neutral".equals(gannSide)) {
                return "NONE";
            }
            return "SOFT";
        }
        if (ictDir.equals(gannSide)) {
            return ictDir.equals("long") ? "ALIGN_LONG" : "ALIGN_SHORT";
        }
        return "CONFLICT";
    }

    private static String grade(double score, boolean aligned) {
        if (score >= 7 && aligned) {
            return "A+";
        }
        if (score >= 5 && aligned) {
            return "A";
        }
        if (score >= 3) {
            return "B";
        }
        if (score >= 1) {
            return "C";
        }
        return "F";
    }

    private static final List<String> ORDER = List.of("F", "C", "B", "A", "A+");

    private static boolean betterThan(String a, String b) {
        return ORDER.indexOf(a) > ORDER.indexOf(b);
    }

    private static String automation(String grade, boolean aligned) {
        if (!aligned) {
            return "deny";
        }
        // MVP is confirm-always (auto paper fill flag OFF): A/A+ are operator-confirmable.
        if ("A+".equals(grade) || "A".equals(grade)) {
            return "confirm";
        }
        return "deny";
    }

    private static Entry entry(IctSnapshot ict, GannSnapshot gann, String direction) {
        IctSnapshot.Zone z = ict.zones().activeEntry();
        if (z != null) {
            return new Entry(z.type(), z.low(), z.high());
        }
        double eq = gann.angle().equilibrium();
        double band = Math.max(0.5, Math.abs(eq) * 0.0005);
        return new Entry("EQ", round(eq - band), round(eq + band));
    }

    private static double stop(IctSnapshot ict, GannSnapshot gann, String direction, Entry entry) {
        double atrBuffer = Math.max(0.5, Math.abs(gann.angle().deviation()) * 0.25 + 1.0);
        if ("short".equals(direction)) {
            double ictStop = Math.max(entry.high(), ict.htf().dealingHigh()) + atrBuffer;
            double gannStop = gann.angle().fan().m2x1() + atrBuffer;
            return Math.max(ictStop, gannStop); // §8 conflict rule: wider stop in paper
        }
        if ("long".equals(direction)) {
            double ictStop = Math.min(entry.low(), ict.htf().dealingLow()) - atrBuffer;
            double gannStop = gann.angle().fan().m1x2() - atrBuffer;
            return Math.min(ictStop, gannStop);
        }
        return 0.0;
    }

    private static List<Double> targets(IctSnapshot ict, GannSnapshot gann, String direction, String mode) {
        if ("T".equals(mode) || "NONE".equals(mode)) {
            return List.of();
        }
        double eq = ict.htf().eq();
        double gannEq = gann.angle().equilibrium();
        if ("short".equals(direction)) {
            double t1 = Math.max(eq, gannEq); // nearer equilibrium below entry
            double t2 = ict.htf().dealingLow();
            return List.of(round(t1), round(t2));
        }
        if ("long".equals(direction)) {
            double t1 = Math.min(eq, gannEq);
            double t2 = ict.htf().dealingHigh();
            return List.of(round(t1), round(t2));
        }
        return List.of();
    }

    private static List<String> invalidations(String direction, double stop, String mode) {
        List<String> out = new ArrayList<>();
        if ("short".equals(direction)) {
            out.add("close_above:" + round(stop));
            out.add("structure_flip:MSS_BULL");
        } else if ("long".equals(direction)) {
            out.add("close_below:" + round(stop));
            out.add("structure_flip:MSS_BEAR");
        } else {
            out.add("session_close");
        }
        out.add("news_blackout");
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
