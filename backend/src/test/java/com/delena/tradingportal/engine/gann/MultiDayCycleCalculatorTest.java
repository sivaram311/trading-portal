package com.delena.tradingportal.engine.gann;

import com.delena.tradingportal.model.OhlcBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiDayCycleCalculatorTest {

    // Swing pivot: Monday 2026-08-03 (confirmed EDT / UTC-4, no DST-boundary risk in this range).
    private static final Instant PIVOT_ORIGIN = Instant.parse("2026-08-03T14:30:00Z");
    private static final double PIVOT_PRICE = 1950.0;

    // Expected trading-day (Mon-Fri) targets from the Monday pivot, verified independently:
    // 3d -> Thu 2026-08-06, 7d -> Wed 2026-08-12, 14d -> Fri 2026-08-21, 21d -> Tue 2026-09-01.

    @Test
    void onPivotDayNoCycleIsNearAndBiasIsNone() {
        Instant asof = Instant.parse("2026-08-03T20:00:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(LocalDate.of(2026, 8, 3), r.pivotDate());
        assertEquals(0, r.tradingDaysFromPivot());
        assertTrue(r.checkpoints().stream().noneMatch(MultiDayCycleCalculator.CycleCheckpoint::near));
        assertNull(r.activeCycleLabel());
        assertEquals("NONE", r.htfBiasHint());
        assertEquals(List.of(3, 7, 14, 21), r.checkpoints().stream()
                .map(MultiDayCycleCalculator.CycleCheckpoint::days).toList());
    }

    @Test
    void oneDayBeforeThreeDayCycleIsNearAndWatching() {
        // 2026-08-05 (Wed) = 2 trading days after the Monday pivot -> 1 day short of the 3d cycle.
        Instant asof = Instant.parse("2026-08-05T20:00:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(2, r.tradingDaysFromPivot());
        MultiDayCycleCalculator.CycleCheckpoint threeDay = checkpoint(r, 3);
        assertEquals(1, threeDay.daysRemaining());
        assertTrue(threeDay.near());
        assertEquals("CYCLE_3D", r.activeCycleLabel());
        assertEquals("CYCLE_WATCH", r.htfBiasHint());
    }

    @Test
    void exactlyOnThreeDayCycleIsNearWithZeroRemaining() {
        Instant asof = Instant.parse("2026-08-06T20:00:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(3, r.tradingDaysFromPivot());
        MultiDayCycleCalculator.CycleCheckpoint threeDay = checkpoint(r, 3);
        assertEquals(LocalDate.of(2026, 8, 6), threeDay.targetDate());
        assertEquals(0, threeDay.daysRemaining());
        assertTrue(threeDay.near());
        assertEquals("CYCLE_3D", r.activeCycleLabel());
        assertEquals("CYCLE_WATCH", r.htfBiasHint());
    }

    @Test
    void oneDayPastThreeDayCycleIsNearAndExhausted() {
        // 2026-08-07 (Thu+1 trading day = Fri) = 4 trading days after pivot -> 1 day past the 3d cycle.
        Instant asof = Instant.parse("2026-08-07T20:00:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(4, r.tradingDaysFromPivot());
        MultiDayCycleCalculator.CycleCheckpoint threeDay = checkpoint(r, 3);
        assertEquals(-1, threeDay.daysRemaining());
        assertTrue(threeDay.near());
        assertEquals("CYCLE_3D", r.activeCycleLabel());
        assertEquals("CYCLE_EXHAUST", r.htfBiasHint());
    }

    @Test
    void sevenDayCycleTargetsExpectedWednesdayAndOnlySevenDayIsActive() {
        Instant asof = Instant.parse("2026-08-12T20:00:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(7, r.tradingDaysFromPivot());
        assertEquals(LocalDate.of(2026, 8, 12), checkpoint(r, 7).targetDate());
        assertTrue(checkpoint(r, 7).near());
        assertFalse(checkpoint(r, 3).near());
        assertFalse(checkpoint(r, 14).near());
        assertEquals("CYCLE_7D", r.activeCycleLabel());
        assertEquals("CYCLE_WATCH", r.htfBiasHint());
    }

    @Test
    void fourteenAndTwentyOneDayTargetsMatchKnownTradingDayCounts() {
        Instant asof = PIVOT_ORIGIN;
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(LocalDate.of(2026, 8, 21), checkpoint(r, 14).targetDate());
        assertEquals(LocalDate.of(2026, 9, 1), checkpoint(r, 21).targetDate());
    }

    @Test
    void targetBarIndexResolvesWhenBarExistsAndIsMinusOneForFutureDates() {
        // D1 bars for every weekday 2026-08-03..2026-08-10 (6 bars); 7/14/21-day targets fall
        // beyond this window and must resolve to -1 (not yet printed).
        List<OhlcBar> bars = d1BarsForWeekdays(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));
        Instant asof = Instant.parse("2026-08-04T20:00:00Z");

        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(bars, PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertEquals(3, checkpoint(r, 3).targetBarIndex());
        assertEquals(-1, checkpoint(r, 7).targetBarIndex());
        assertEquals(-1, checkpoint(r, 14).targetBarIndex());
        assertEquals(-1, checkpoint(r, 21).targetBarIndex());
    }

    @Test
    void anniversaryOnePlusOneDayIsNear() {
        Instant asof = Instant.parse("2027-08-04T14:30:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        MultiDayCycleCalculator.Anniversary oneYear = r.anniversaries().stream()
                .filter(a -> a.yearsElapsed() == 1).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2027, 8, 3), oneYear.date());
        assertEquals(-1, oneYear.daysFromAsof());
        assertTrue(oneYear.near());

        MultiDayCycleCalculator.Anniversary twoYear = r.anniversaries().stream()
                .filter(a -> a.yearsElapsed() == 2).findFirst().orElseThrow();
        assertFalse(twoYear.near());
    }

    @Test
    void nullPivotOriginFailsSoftWithNoneBias() {
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), null, PIVOT_PRICE, Instant.now());

        assertNull(r.pivotDate());
        assertEquals(0, r.tradingDaysFromPivot());
        assertNull(r.activeCycleLabel());
        assertEquals("NONE", r.htfBiasHint());
        assertTrue(r.checkpoints().stream().allMatch(c -> c.targetBarIndex() == -1 && !c.near()));
    }

    @Test
    void neverEmitsAnOrderOrDirectionField() {
        // Contract check: htfBiasHint values are labels only, never side/qty fields.
        Instant asof = Instant.parse("2026-08-06T20:00:00Z");
        MultiDayCycleCalculator.Result r = MultiDayCycleCalculator.compute(List.of(), PIVOT_ORIGIN, PIVOT_PRICE, asof);

        assertTrue(List.of("NONE", "CYCLE_WATCH", "CYCLE_EXHAUST").contains(r.htfBiasHint()));
    }

    private static MultiDayCycleCalculator.CycleCheckpoint checkpoint(MultiDayCycleCalculator.Result r, int days) {
        return r.checkpoints().stream().filter(c -> c.days() == days).findFirst().orElseThrow();
    }

    private static List<OhlcBar> d1BarsForWeekdays(LocalDate start, LocalDate end) {
        List<OhlcBar> out = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek().getValue() < 6) {
                Instant ts = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(21 * 3600L);
                out.add(new OhlcBar("XAUUSD", "D1", ts, ts, 1950, 1960, 1940, 1955, 1000, ts));
            }
            d = d.plusDays(1);
        }
        return out;
    }
}
