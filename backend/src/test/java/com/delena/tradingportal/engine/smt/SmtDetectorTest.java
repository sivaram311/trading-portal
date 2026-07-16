package com.delena.tradingportal.engine.smt;

import com.delena.tradingportal.model.OhlcBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtDetectorTest {

    private final SmtDetector detector = new SmtDetector();
    private static final Instant T0 = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void goldHigherHighWithDxyLowerHighYieldsGoldStrong() {
        List<OhlcBar> gold = goldHigherHighSeries();
        List<OhlcBar> dxy = dxyLowerHighSeries();

        SmtDetector.SmtSignal signal = detector.detect(gold, List.of(), dxy, List.of());

        assertEquals("gold_strong", signal.bias());
        assertTrue(signal.divergent());
        assertTrue(signal.detail().contains("GOLD_STRONG"));
    }

    @Test
    void emptyDxyBarsFailSoft() {
        SmtDetector.SmtSignal signal = detector.detect(goldHigherHighSeries(), List.of(), List.of(), List.of());

        assertEquals("none", signal.bias());
        assertFalse(signal.divergent());
        assertEquals("NO_DXY_DATA", signal.detail());
    }

    // Gold: HH + HL — swing highs at 5 (2000) and 15 (2010); swing lows at 8 (1980) and 18 (1985).
    private static List<OhlcBar> goldHigherHighSeries() {
        double[] highs = {
                1995, 1995, 1995, 1995, 1995, 2000, 1992, 1992, 1992, 1992, 1992, 1992, 1992, 1992, 1992, 2010,
                1995, 1995, 1995, 1995, 1995, 1995, 1995, 1995, 1995, 1995
        };
        double[] lows = {
                1990, 1990, 1990, 1990, 1990, 1990, 1990, 1990, 1980, 1990, 1990, 1990, 1990, 1990, 1990, 1990,
                1990, 1990, 1985, 1990, 1990, 1990, 1990, 1990, 1990, 1990
        };
        return explicitSeries("XAUUSD", highs, lows);
    }

    // DXY: LH + LL — swing highs at 5 (104.0) and 15 (103.2); swing lows at 8 (102.0) and 18 (101.5).
    private static List<OhlcBar> dxyLowerHighSeries() {
        double[] highs = {
                103.5, 103.5, 103.5, 103.5, 103.5, 104.0, 103.2, 103.2, 103.2, 103.2, 103.2, 103.2, 103.2, 103.1, 103.1, 103.2,
                103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0
        };
        double[] lows = {
                103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 102.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0,
                103.0, 103.0, 101.5, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0, 103.0
        };
        return explicitSeries("DXY", highs, lows);
    }

    private static List<OhlcBar> explicitSeries(String symbol, double[] highs, double[] lows) {
        List<OhlcBar> out = new ArrayList<>();
        for (int i = 0; i < highs.length; i++) {
            double open = (highs[i] + lows[i]) / 2.0;
            out.add(new OhlcBar(symbol, "M15", T0.plusSeconds(i * 900L), T0.plusSeconds(i * 900L),
                    open, highs[i], lows[i], open, 100, null));
        }
        return out;
    }
}
