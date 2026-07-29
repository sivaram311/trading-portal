package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mitigation Block detection + selectEntry scoring (DEEP-ALGORITHMS §2.8). Bar fixtures include an
 * explicit displacement-away leg (a bar that closes beyond the OB and never overlaps it) before the
 * touch, so these tests hold regardless of whether {@link MitigationDetector} requires that leg
 * before scanning for a touch. They intentionally avoid asserting the exact ts a mitigation is
 * stamped at, since that is an internal detail; they assert the outcome (mitigated or not) plus the
 * emitted zone's type/direction/range. The selectEntry test covers the part of the contract owned
 * by {@link IctEngine}: scoring boost + {@code MITIGATION_ACTIVE} eligibility.
 */
class MitigationTest {

    private static final Instant BASE = Instant.parse("2026-07-15T12:00:00Z");
    private static final List<IctSnapshot.Zone> NONE = List.of();

    @Test
    void bullObTouchWithRejectionWickThenContinuesEmitsMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 102, 103),     // OB candle itself
                bar(t1, 106, 110, 106, 109),     // displacement away: closes above OB high, no overlap
                bar(t2, 103, 106, 100.5, 105.5)  // touch (rejection wick) that closes back above OB high
        );

        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(bars, List.of(bullOb));

        assertEquals(1, mitigations.size());
        assertEquals("MITIGATION", mitigations.get(0).type());
        assertEquals("bull", mitigations.get(0).direction());
        assertEquals("mitigated", mitigations.get(0).state());
        assertEquals(100.0, mitigations.get(0).low(), 0.01);
        assertEquals(105.0, mitigations.get(0).high(), 0.01);
    }

    @Test
    void bearObTouchWithRejectionWickThenContinuesEmitsMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bearOb = new IctSnapshot.Zone("OB", "bear", 200.0, 205.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 201, 203, 200, 202),        // OB candle itself
                bar(t1, 199, 199, 195, 196),         // displacement away: closes below OB low, no overlap
                bar(t2, 202, 204.5, 199, 199.5)      // touch (rejection wick) that closes back below OB low
        );

        List<IctSnapshot.Zone> mitigations = MitigationDetector.deriveMitigations(bars, List.of(bearOb));

        assertEquals(1, mitigations.size());
        assertEquals("MITIGATION", mitigations.get(0).type());
        assertEquals("bear", mitigations.get(0).direction());
        assertEquals("mitigated", mitigations.get(0).state());
    }

    @Test
    void fullCloseThroughIsBreakerNotMitigation() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 102, 103),
                bar(t1, 106, 110, 106, 109),  // displacement away
                bar(t2, 103, 104, 98, 99)     // full close-through below OB low -> breaker territory
        );

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());
    }

    @Test
    void touchWithoutReactionEmitsNoMitigationEvenIfPriceContinues() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 102, 103),
                bar(t1, 106, 110, 106, 109),  // displacement away
                // Touches the OB but the candle has no meaningful rejection wick / engulfing,
                // and does not itself close beyond the OB either.
                bar(t2, 103, 103.5, 102.8, 103.2)
        );

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());
    }

    @Test
    void fvgConfluenceConfirmsReactionAtOverload() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        var overlappingFvg = new IctSnapshot.Zone("FVG", "bull", 101.0, 103.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 104, 105, 102, 103),
                bar(t1, 106, 110, 106, 109),          // displacement away
                bar(t2, 103.5, 106, 102.9, 105.8)     // touches OB, no rejection wick/engulfing, closes back above high
        );

        assertTrue(MitigationDetector.deriveMitigations(bars, List.of(bullOb)).isEmpty());

        List<IctSnapshot.Zone> withFvg = MitigationDetector.deriveMitigations(bars, List.of(bullOb),
                List.of(overlappingFvg));

        assertEquals(1, withFvg.size());
        assertEquals("MITIGATION", withFvg.get(0).type());
    }

    @Test
    void selectEntryBoostsCandidateOverlappingMitigationAndAddsReason() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");
        // Overlaps OTE but stays clear of the sweet spot and the mitigation zone below.
        var oteOnlyOb = new IctSnapshot.Zone("OB", "bull", ote.deep(), ote.deep() + 2, "fresh", BASE);
        var mitigatedFvg = new IctSnapshot.Zone("FVG", "bull", ote.sweet() - 0.5, ote.sweet() + 0.5, "fresh", BASE);
        var mitigation = new IctSnapshot.Zone("MITIGATION", "bull",
                ote.sweet() - 0.5, ote.sweet() + 0.5, "mitigated", BASE);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(oteOnlyOb), List.of(mitigatedFvg), NONE, NONE, List.of(mitigation), "long", ote);

        assertEquals(mitigatedFvg, pick.zone());
        assertTrue(pick.mitigationOverlap());
    }

    private static OhlcBar bar(Instant ts, double open, double high, double low, double close) {
        return new OhlcBar("XAUUSD", "M15", ts, ts, open, high, low, close, 100, ts);
    }
}
