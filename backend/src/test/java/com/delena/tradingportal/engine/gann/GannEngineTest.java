package com.delena.tradingportal.engine.gann;

import com.delena.tradingportal.model.GannSnapshot;
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

/** Multi-day cycle wiring (GANN-CYCLE-ENGINE.md §7). Intraday behavior is covered elsewhere. */
class GannEngineTest {

    private final GannEngine engine = new GannEngine();
    private static final Instant ASOF = Instant.parse("2026-06-12T15:00:00Z"); // NY 11:00, session 2026-06-12

    @Test
    void computeWiresMultiDayCheckpointsAndReasonFromD1Swing() {
        GannSnapshot snap = engine.compute(m5Window(), d1SeriesWithSwingLow(), ASOF, GannConfig.defaults(), "NY_OPEN");

        assertTrue(snap.reasons().contains("MULTI_DAY_CYCLE_NEAR"));

        GannSnapshot.Cycles cycles = snap.cycles();
        assertEquals("MULTI_DAY_7", cycles.multiDayLabel());
        assertEquals(4, cycles.multiDayCheckpoints().size());
        assertEquals(1900.0, cycles.multiDayOrigin().price(), 0.01);
        assertEquals(LocalDate.parse("2026-06-03"), cycles.multiDayOrigin().date());
        assertTrue(cycles.multiDayCheckpoints().stream()
                .anyMatch(c -> c.dayCount() == 7 && c.activeToday()));

        // Intraday session-cycle fields are unaffected by the multi-day overlay.
        assertTrue(cycles.sessionFraction() >= 0);
    }

    @Test
    void computeFallsBackGracefullyWhenD1BarsMissing() {
        GannSnapshot snap = engine.compute(m5Window(), null, ASOF, GannConfig.defaults(), "NY_OPEN");

        assertFalse(snap.reasons().contains("MULTI_DAY_CYCLE_NEAR"));
        assertNull(snap.cycles().multiDayOrigin());
        assertTrue(snap.cycles().multiDayCheckpoints().isEmpty());
        assertNull(snap.cycles().multiDayLabel());
    }

    @Test
    void multiDayOverlayNeverAffectsQualityScore() {
        GannSnapshot withMultiDay = engine.compute(m5Window(), d1SeriesWithSwingLow(), ASOF, GannConfig.defaults(), "NY_OPEN");
        GannSnapshot withoutMultiDay = engine.compute(m5Window(), null, ASOF, GannConfig.defaults(), "NY_OPEN");

        assertEquals(withoutMultiDay.quality(), withMultiDay.quality());
        assertEquals(withoutMultiDay.gannBias(), withMultiDay.gannBias());
    }

    @Test
    void preferEntryBarsFallsBackToM15WhenM5TooShort() {
        List<OhlcBar> m15 = m5Window(); // reuse 5-bar fixture as "M15"
        List<OhlcBar> m5Short = m15.subList(0, 2);
        assertEquals(m15, GannEngine.preferEntryBars(m5Short, m15));
        assertEquals(m15, GannEngine.preferEntryBars(List.of(), m15));
        assertEquals(m5Window(), GannEngine.preferEntryBars(m5Window(), m15));
    }

    @Test
    void emptyBarsYieldDataGap() {
        GannSnapshot snap = engine.compute(List.of(), null, ASOF, GannConfig.defaults(), "NY_OPEN");
        assertTrue(snap.reasons().contains("DATA_GAP"));
        assertEquals(0, snap.quality());
    }

    // ------------------------------------------------------------------ fixtures

    private static List<OhlcBar> m5Window() {
        List<OhlcBar> bars = new ArrayList<>();
        String[] times = {"11:00", "11:05", "11:10", "11:15", "15:00"}; // UTC on 2026-06-12
        for (String t : times) {
            Instant ts = Instant.parse("2026-06-12T" + t + ":00Z");
            bars.add(new OhlcBar("XAUUSD", "M5", ts, ts, 1955, 1957, 1953, 1956, 500, ts));
        }
        return bars;
    }

    // Same fixture as MultiDayCycleCalculatorTest: swing low anchored at 2026-06-03.
    private static List<OhlcBar> d1SeriesWithSwingLow() {
        String[] dates = {
                "2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05",
                "2026-06-08", "2026-06-09", "2026-06-10", "2026-06-11", "2026-06-12"
        };
        double[] lows = {1950, 1945, 1900, 1940, 1945, 1948, 1950, 1952, 1954, 1956};
        double[] highs = {1960, 1955, 1910, 1950, 1955, 1958, 1960, 1962, 1964, 1966};
        List<OhlcBar> bars = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            Instant ts = Instant.parse(dates[i] + "T16:00:00Z");
            bars.add(new OhlcBar("XAUUSD", "D1", ts, ts, lows[i], highs[i], lows[i], highs[i], 1000, ts));
        }
        return bars;
    }
}
