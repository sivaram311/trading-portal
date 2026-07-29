package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitigationDetectorTest {

    private static final Instant BASE = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void bullObMitigatedByRejectionWickThenContinuation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),      // OB creation candle (bearish)
                bar(t1, 106, 110, 106, 109),      // displacement leg leaves the zone cleanly
                bar(t2, 103.2, 104.5, 101, 104),  // return touch: deep lower wick, bullish close (rejection)
                bar(t3, 104, 108, 103.5, 107.5)   // continuation: close back above OB high
        );

        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(bars, List.of(bullOb));

        assertEquals(1, mitigations.size());
        IctSnapshot.Zone m = mitigations.get(0);
        assertEquals("MITIGATION", m.type());
        assertEquals("bull", m.direction());
        assertEquals(100.0, m.low(), 0.01);
        assertEquals(105.0, m.high(), 0.01);
        assertEquals("mitigated", m.state());
        assertEquals(t2, m.ts()); // stamped at the touch bar, not the continuation bar
    }

    @Test
    void bearObMitigatedByRejectionWickThenContinuation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bearOb = new IctSnapshot.Zone("OB", "bear", 200.0, 205.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 201, 205, 200, 204),        // OB creation candle (bullish)
                bar(t1, 199, 199, 190, 191),        // displacement leg leaves the zone cleanly
                bar(t2, 201, 204, 199.5, 200.5),    // return touch: deep upper wick, bearish close
                bar(t3, 200, 201, 194, 195)         // continuation: close back below OB low
        );

        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(bars, List.of(bearOb));

        assertEquals(1, mitigations.size());
        IctSnapshot.Zone m = mitigations.get(0);
        assertEquals("MITIGATION", m.type());
        assertEquals("bear", m.direction());
        assertEquals(200.0, m.low(), 0.01);
        assertEquals(205.0, m.high(), 0.01);
        assertEquals(t2, m.ts());
    }

    @Test
    void noMitigationWhenObInvalidatedByBodyCloseThrough() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),   // OB creation candle
                bar(t1, 106, 110, 106, 109),   // displacement leaves the zone cleanly
                bar(t2, 102, 103, 94, 95)      // full close below OB low -> invalidated, not mitigated
        );

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());
    }

    @Test
    void noFalsePositiveOnFirstCreationCandle() {
        Instant t0 = BASE;
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        // Only the OB's own creation candle exists — its own range trivially overlaps the zone,
        // but there is no bar after it, so it must never be mistaken for a mitigation touch.
        List<OhlcBar> bars = List.of(bar(t0, 104, 105, 100, 101));

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());
    }

    @Test
    void reactionConfirmedButContinuationNeverConfirmedYieldsNoMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),        // OB creation candle
                bar(t1, 106, 110, 106, 109),        // displacement leaves the zone cleanly
                bar(t2, 103.2, 104.5, 101, 104),    // touch with a genuine rejection wick reaction
                bar(t3, 104, 104.8, 103, 104.3)     // stalls — never closes back above OB high
        );

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());
    }

    @Test
    void touchWithoutReactionDoesNotEmitMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        Instant t4 = BASE.plusSeconds(3600);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),         // OB creation candle
                bar(t1, 106, 110, 106, 109),         // displacement leaves the zone cleanly
                bar(t2, 103, 103.5, 102, 102.5),     // touches zone but no wick/engulf reaction (bearish close)
                bar(t3, 102.6, 103, 102.4, 102.9),   // small bullish grind — too small a wick, doesn't engulf t2
                bar(t4, 102.9, 110, 102.8, 109)      // eventually continues, but no reaction was ever confirmed
        );

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());
    }

    @Test
    void bullishEngulfingReactionConfirmsMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),          // OB creation candle
                bar(t1, 106, 110, 106, 109),          // displacement leaves the zone cleanly
                bar(t2, 108, 109, 106, 107),          // bearish pullback candle, does not overlap zone yet
                bar(t3, 105.2, 109, 104.9, 108.5)     // bullish candle engulfing t2, dips into + closes above zone
        );

        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(bars, List.of(bullOb));

        assertEquals(1, mitigations.size());
        assertEquals("MITIGATION", mitigations.get(0).type());
        assertEquals(t3, mitigations.get(0).ts());
    }

    @Test
    void fvgConfluenceReactionConfirmsMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        var overlappingFvg = new IctSnapshot.Zone("FVG", "bull", 102.0, 106.0, "fresh", t1);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),        // OB creation candle
                bar(t1, 106, 110, 106, 109),        // displacement leaves the zone cleanly
                bar(t2, 104, 104.5, 103, 103.5),    // plain bearish touch — no wick/engulf reaction on its own
                bar(t3, 103.6, 108, 103.3, 107)     // continuation back above OB high, does not engulf t2
        );

        // Without FVG confluence, the plain touch candle has no reaction and no mitigation is found.
        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());

        // With an overlapping bull FVG inside the OB, confluence confirms the reaction.
        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(
                bars, List.of(bullOb), List.of(overlappingFvg));

        assertEquals(1, mitigations.size());
        assertEquals(t2, mitigations.get(0).ts());
    }

    @Test
    void multipleObsAreProcessedIndependently() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var mitigatedOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        var untouchedOb = new IctSnapshot.Zone("OB", "bull", 300.0, 305.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 100, 101),
                bar(t1, 106, 110, 106, 109),
                bar(t2, 103.2, 104.5, 101, 104),
                bar(t3, 104, 108, 103.5, 107.5)
        );

        // untouchedOb's zone never overlaps these bars at all, so it simply never mitigates.
        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(
                bars, List.of(mitigatedOb, untouchedOb));

        assertEquals(1, mitigations.size());
        assertEquals(100.0, mitigations.get(0).low(), 0.01);
    }

    private static OhlcBar bar(Instant ts, double open, double high, double low, double close) {
        return new OhlcBar("XAUUSD", "M15", ts, ts, open, high, low, close, 100, ts);
    }
}
