package com.delena.tradingportal.engine.gann;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.model.OhlcBar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Multi-day Gann cycle checkpoints (docs/theory/GANN-INTRADAY-TIME-CYCLES.md §7.2, research tier).
 *
 * <p>Covers the two v1 items from §7.2 that are not deferred:
 * <ul>
 *   <li>3 / 7 / 14 / 21 trading-day checkpoints from a swing pivot ({@link #CYCLE_DAYS}).</li>
 *   <li>Calendar-year anniversaries of the same pivot, emitted as annotations.</li>
 * </ul>
 * Square-of-9 time (degrees → days) is explicitly deferred per spec and is not implemented here.
 *
 * <p>Trading days are counted as Mon–Fri calendar days (no holiday calendar available yet); this
 * is a deliberate simplification appropriate for a "research tier / journal only" feature.
 *
 * <p><b>Output is observe/journal-only.</b> {@link Result#htfBiasHint()} feeds HTF bias labels
 * for a human or the confluence framework — this class never selects a trade direction or size
 * and must not be wired into order placement.
 */
public final class MultiDayCycleCalculator {

    /** Trading-day cycle lengths studied per §7.2 (research tier). */
    public static final List<Integer> CYCLE_DAYS = List.of(3, 7, 14, 21);

    private static final int NEAR_TOLERANCE_DAYS = 1;
    private static final int ANNIVERSARY_YEARS_AHEAD = 3;

    private MultiDayCycleCalculator() {
    }

    /** One cycle checkpoint. {@code targetBarIndex} is -1 when the target day has no bar yet (future). */
    public record CycleCheckpoint(
            int days,
            LocalDate targetDate,
            int targetBarIndex,
            int daysRemaining,
            boolean near
    ) {
    }

    /** Calendar-year anniversary of the pivot date; {@code daysFromAsof} is signed (negative = past). */
    public record Anniversary(int yearsElapsed, LocalDate date, int daysFromAsof, boolean near) {
    }

    public record Result(
            Instant asof,
            LocalDate pivotDate,
            double pivotPrice,
            int tradingDaysFromPivot,
            List<CycleCheckpoint> checkpoints,
            List<Anniversary> anniversaries,
            String activeCycleLabel,
            String htfBiasHint
    ) {
    }

    /**
     * @param bars        D1 (or H4) bars; used only to resolve {@code targetBarIndex} lookups, not
     *                    for trading-day counting (see class doc).
     * @param pivotOrigin swing pivot origin (confirmed SH/SL) timestamp
     * @param pivotPrice  swing pivot price, carried through for caller labeling/annotation
     * @param asof        evaluation instant ("now")
     */
    public static Result compute(List<OhlcBar> bars, Instant pivotOrigin, double pivotPrice, Instant asof) {
        if (pivotOrigin == null || asof == null) {
            return empty(asof, pivotOrigin, pivotPrice);
        }
        LocalDate pivotDate = NyTime.sessionDate(pivotOrigin);
        LocalDate asofDate = NyTime.sessionDate(asof);
        int tradingDaysFromPivot = tradingDaysBetween(pivotDate, asofDate);

        List<CycleCheckpoint> checkpoints = new ArrayList<>();
        for (int cycle : CYCLE_DAYS) {
            LocalDate targetDate = addTradingDays(pivotDate, cycle);
            int targetBarIndex = barIndexForDate(bars, targetDate);
            int daysRemaining = cycle - tradingDaysFromPivot;
            boolean near = Math.abs(daysRemaining) <= NEAR_TOLERANCE_DAYS;
            checkpoints.add(new CycleCheckpoint(cycle, targetDate, targetBarIndex, daysRemaining, near));
        }

        List<Anniversary> anniversaries = anniversaries(pivotDate, asofDate);

        Comparator<CycleCheckpoint> byCloseness = Comparator
                .comparingInt((CycleCheckpoint c) -> Math.abs(c.daysRemaining()))
                .thenComparingInt(CycleCheckpoint::days);
        CycleCheckpoint active = checkpoints.stream().filter(CycleCheckpoint::near).min(byCloseness).orElse(null);
        String activeCycleLabel = active == null ? null : "CYCLE_" + active.days() + "D";
        String htfBiasHint = biasHint(active);

        return new Result(asof, pivotDate, pivotPrice, tradingDaysFromPivot, checkpoints, anniversaries,
                activeCycleLabel, htfBiasHint);
    }

    // ------------------------------------------------------------------ helpers

    private static Result empty(Instant asof, Instant pivotOrigin, double pivotPrice) {
        LocalDate pivotDate = pivotOrigin == null ? null : NyTime.sessionDate(pivotOrigin);
        List<CycleCheckpoint> checkpoints = CYCLE_DAYS.stream()
                .map(c -> new CycleCheckpoint(c, null, -1, 0, false))
                .toList();
        return new Result(asof, pivotDate, pivotPrice, 0, checkpoints, List.of(), null, "NONE");
    }

    private static String biasHint(CycleCheckpoint active) {
        if (active == null) {
            return "NONE";
        }
        return active.daysRemaining() < 0 ? "CYCLE_EXHAUST" : "CYCLE_WATCH";
    }

    /** Signed count of Mon-Fri calendar days in the half-open-ish interval between the two dates. */
    private static int tradingDaysBetween(LocalDate from, LocalDate to) {
        if (to.isEqual(from)) {
            return 0;
        }
        if (to.isAfter(from)) {
            int count = 0;
            LocalDate d = from.plusDays(1);
            while (!d.isAfter(to)) {
                if (isWeekday(d)) {
                    count++;
                }
                d = d.plusDays(1);
            }
            return count;
        }
        return -tradingDaysBetween(to, from);
    }

    private static LocalDate addTradingDays(LocalDate from, int n) {
        LocalDate d = from;
        int added = 0;
        int step = n >= 0 ? 1 : -1;
        int target = Math.abs(n);
        while (added < target) {
            d = d.plusDays(step);
            if (isWeekday(d)) {
                added++;
            }
        }
        return d;
    }

    private static boolean isWeekday(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    private static int barIndexForDate(List<OhlcBar> bars, LocalDate date) {
        if (bars == null || date == null) {
            return -1;
        }
        for (int i = 0; i < bars.size(); i++) {
            OhlcBar b = bars.get(i);
            if (b.ts() != null && NyTime.sessionDate(b.ts()).isEqual(date)) {
                return i;
            }
        }
        return -1;
    }

    private static List<Anniversary> anniversaries(LocalDate pivotDate, LocalDate asofDate) {
        List<Anniversary> out = new ArrayList<>();
        for (int years = 1; years <= ANNIVERSARY_YEARS_AHEAD; years++) {
            LocalDate anniv = pivotDate.plusYears(years);
            int daysFromAsof = (int) ChronoUnit.DAYS.between(asofDate, anniv);
            boolean near = Math.abs(daysFromAsof) <= NEAR_TOLERANCE_DAYS;
            out.add(new Anniversary(years, anniv, daysFromAsof, near));
        }
        return out;
    }
}
