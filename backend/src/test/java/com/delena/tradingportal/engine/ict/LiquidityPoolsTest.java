package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidityPoolsTest {

    private static final Instant BASE = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void clustersEqualHighsIntoEqhPool() {
        var swings = List.of(
                new IctSnapshot.Swing("high", 2650.0, BASE, 4),
                new IctSnapshot.Swing("high", 2650.30, BASE, 10),
                new IctSnapshot.Swing("high", 2650.50, BASE, 16)
        );
        List<IctSnapshot.Pool> pools = LiquidityPools.detectEqualLevels(swings, 0.6);

        assertEquals(1, pools.size());
        assertEquals("EQH", pools.get(0).name());
        assertEquals("high", pools.get(0).side());
        assertEquals(2650.27, pools.get(0).price(), 0.01);
    }

    @Test
    void manyNearEqualHighsMergeAndCapAtThree() {
        List<IctSnapshot.Swing> swings = new ArrayList<>();
        // 8 distinct clusters spaced > eps apart → should cap to 3 nearest mid=2655
        for (int i = 0; i < 8; i++) {
            double p = 2640.0 + i * 5.0;
            swings.add(new IctSnapshot.Swing("high", p, BASE, i * 2));
            swings.add(new IctSnapshot.Swing("high", p + 0.2, BASE, i * 2 + 1));
        }
        List<IctSnapshot.Pool> pools = LiquidityPools.detectEqualLevels(swings, 0.6, 2655.0, 3);
        assertEquals(3, pools.size());
        assertTrue(pools.stream().allMatch(p -> "EQH".equals(p.name())));
        // nearest to 2655 among 2640,2645,...,2675
        assertTrue(pools.stream().anyMatch(p -> Math.abs(p.price() - 2655.0) < 1.0
                || Math.abs(p.price() - 2650.0) < 1.0
                || Math.abs(p.price() - 2660.0) < 1.0));
    }

    @Test
    void mergeNearPoolsCollapsesDuplicatesWithinEps() {
        var pools = List.of(
                new IctSnapshot.Pool("EQH", 2650.0, "high"),
                new IctSnapshot.Pool("EQH", 2650.40, "high"),
                new IctSnapshot.Pool("EQH", 2660.0, "high")
        );
        List<IctSnapshot.Pool> merged = LiquidityPools.mergeNearPools(pools, 0.6);
        assertEquals(2, merged.size());
    }

    @Test
    void clustersEqualLowsIntoEqlPool() {
        var swings = List.of(
                new IctSnapshot.Swing("low", 2640.0, BASE, 5),
                new IctSnapshot.Swing("low", 2640.40, BASE, 11)
        );
        List<IctSnapshot.Pool> pools = LiquidityPools.detectEqualLevels(swings, 0.6);

        assertEquals(1, pools.size());
        assertEquals("EQL", pools.get(0).name());
        assertEquals("low", pools.get(0).side());
        assertEquals(2640.20, pools.get(0).price(), 0.01);
    }

    @Test
    void roundNumberNear2400EmitsRound100() {
        List<IctSnapshot.Pool> pools = LiquidityPools.roundNumberPools(2397.0, 10.0);

        assertTrue(pools.stream().anyMatch(p -> "ROUND_100".equals(p.name()) && p.price() == 2400.0));
        assertTrue(pools.stream().anyMatch(p -> "ROUND_50".equals(p.name()) && p.price() == 2400.0));
        assertTrue(pools.stream().anyMatch(p -> "ROUND_10".equals(p.name()) && p.price() == 2400.0));
        assertTrue(pools.stream().anyMatch(p -> "ROUND_5".equals(p.name()) && p.price() == 2395.0));
    }

    @Test
    void roundNumbersUseMaxOfTwoAtrOrFiftyPoints() {
        // ATR=1 -> range=50 (floor), mid 2397 still reaches 2400.
        assertTrue(LiquidityPools.roundNumberPools(2397.0, 1.0).stream()
                .anyMatch(p -> "ROUND_100".equals(p.name())));
        // ATR=30 -> range=60; mid 2500 is on a round, all four types present.
        assertEquals(4, LiquidityPools.roundNumberPools(2500.0, 30.0).size());
    }

    @Test
    void m15SwingsProduceEqhFromSyntheticBars() {
        List<OhlcBar> m15 = syntheticEqualHighBars();
        List<IctSnapshot.Swing> swings = IctEngine.swings(m15, 2);
        List<IctSnapshot.Pool> eq = LiquidityPools.detectEqualLevels(swings, 0.6);

        assertFalse(eq.isEmpty(), "expected EQH from synthetic equal highs, swings=" + swings);
        assertEquals("EQH", eq.get(0).name());
        assertEquals("high", eq.get(0).side());
    }

    @Test
    void buildPoolsOrderIsAhhPdhThenEqhThenRounds() {
        IctEngine engine = new IctEngine();
        Instant asof = Instant.parse("2026-07-15T12:30:00Z");
        List<OhlcBar> m15 = sessionBarsWithEqualHighs(asof);

        IctSnapshot snap = engine.compute(List.of(), List.of(), m15, List.of(), asof, IctConfig.defaults());
        List<IctSnapshot.Pool> pools = snap.liquidity().pools();

        assertFalse(pools.isEmpty());
        int ahh = indexOf(pools, "AHH");
        int pdh = indexOf(pools, "PDH");
        int eqh = indexOf(pools, "EQH");
        int round100 = indexOf(pools, "ROUND_100");

        assertTrue(ahh >= 0 && pdh > ahh, "PDH should follow AHH");
        assertTrue(eqh > pdh, "EQH should follow session pools");
        assertTrue(round100 > eqh, "ROUND pools should follow EQH/EQL");
    }

    private static int indexOf(List<IctSnapshot.Pool> pools, String name) {
        for (int i = 0; i < pools.size(); i++) {
            if (name.equals(pools.get(i).name())) {
                return i;
            }
        }
        return -1;
    }

    /** Two M15 swing highs within 0.6 pts (n=2). */
    private static List<OhlcBar> syntheticEqualHighBars() {
        double[] highs = {2640, 2645, 2648, 2650.0, 2648, 2645, 2643, 2646, 2649, 2650.3, 2648, 2645, 2640, 2643, 2646};
        List<OhlcBar> bars = new ArrayList<>();
        for (int i = 0; i < highs.length; i++) {
            double h = highs[i];
            double l = h - 4;
            bars.add(bar(BASE.plusSeconds(i * 900L), h - 1, h, l, h - 0.5));
        }
        return bars;
    }

    /** Prior-day + pre-NY-open + equal-high M15 series for ordering integration. */
    private static List<OhlcBar> sessionBarsWithEqualHighs(Instant asof) {
        List<OhlcBar> bars = new ArrayList<>();
        Instant prevDay = Instant.parse("2026-07-14T14:00:00Z");
        for (int i = 0; i < 8; i++) {
            double px = 2380 + i;
            bars.add(bar(prevDay.plusSeconds(i * 900L), px, px + 2, px - 2, px + 1));
        }
        Instant preOpen = Instant.parse("2026-07-15T06:00:00Z");
        for (int i = 0; i < 4; i++) {
            double px = 2390 + i;
            bars.add(bar(preOpen.plusSeconds(i * 900L), px, px + 3, px - 1, px + 1));
        }
        bars.addAll(syntheticEqualHighBarsAt(Instant.parse("2026-07-15T08:00:00Z"), 2397.0, 2397.3));
        return bars;
    }

    private static List<OhlcBar> syntheticEqualHighBarsAt(Instant start, double peak1, double peak2) {
        double[] highs = {
                peak1 - 10, peak1 - 7, peak1 - 3, peak1, peak1 - 3, peak1 - 7,
                peak1 - 9, peak2 - 7, peak2 - 3, peak2, peak2 - 3, peak2 - 7,
                peak2 - 10, peak2 - 8, peak2 - 5
        };
        List<OhlcBar> bars = new ArrayList<>();
        for (int i = 0; i < highs.length; i++) {
            double h = highs[i];
            bars.add(bar(start.plusSeconds(i * 900L), h - 1, h, h - 4, h - 0.5));
        }
        return bars;
    }

    private static OhlcBar bar(Instant ts, double open, double high, double low, double close) {
        return new OhlcBar("XAUUSD", "M15", ts, ts, open, high, low, close, 100, ts);
    }
}
