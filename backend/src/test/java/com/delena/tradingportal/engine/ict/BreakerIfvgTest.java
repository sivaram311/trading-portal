package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakerIfvgTest {

    private static final Instant BASE = Instant.parse("2026-07-15T12:00:00Z");
    private static final List<IctSnapshot.Zone> NONE = List.of();

    @Test
    void bullObCloseThroughLowEmitsBearBreaker() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 102, 105, 100, 101),
                bar(t1, 101, 103, 100, 102),
                bar(t2, 102, 103, 98, 99) // close below OB low → failed bull OB
        );

        List<IctSnapshot.Zone> breakers = IctEngine.deriveBreakers(bars, List.of(bullOb));

        assertEquals(1, breakers.size());
        assertEquals("BREAKER", breakers.get(0).type());
        assertEquals("bear", breakers.get(0).direction());
        assertEquals("fresh", breakers.get(0).state());
        assertEquals(100.0, breakers.get(0).low(), 0.01);
        assertEquals(105.0, breakers.get(0).high(), 0.01);
        assertEquals(t2, breakers.get(0).ts());
    }

    @Test
    void bearObCloseThroughHighEmitsBullBreaker() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        Instant t2 = BASE.plusSeconds(1800);
        var bearOb = new IctSnapshot.Zone("OB", "bear", 200.0, 205.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 204, 205, 200, 201),
                bar(t1, 201, 203, 199, 200),
                bar(t2, 200, 206, 199, 206) // close above OB high → failed bear OB
        );

        List<IctSnapshot.Zone> breakers = IctEngine.deriveBreakers(bars, List.of(bearOb));

        assertEquals(1, breakers.size());
        assertEquals("BREAKER", breakers.get(0).type());
        assertEquals("bull", breakers.get(0).direction());
        assertEquals("fresh", breakers.get(0).state());
    }

    @Test
    void bullFvgCloseThroughGapEmitsBearIfvg() {
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bullFvg = new IctSnapshot.Zone("FVG", "bull", 110.0, 115.0, "fresh", t2);
        List<OhlcBar> bars = List.of(
                bar(BASE, 108, 110, 107, 109),
                bar(BASE.plusSeconds(900), 109, 112, 108, 111),
                bar(t2, 114, 118, 115, 117),
                bar(t3, 112, 113, 106, 107) // close below FVG low
        );

        List<IctSnapshot.Zone> ifvgs = IctEngine.deriveIfvgs(bars, List.of(bullFvg));

        assertEquals(1, ifvgs.size());
        assertEquals("IFVG", ifvgs.get(0).type());
        assertEquals("bear", ifvgs.get(0).direction());
        assertEquals("inverted", ifvgs.get(0).state());
        assertEquals(110.0, ifvgs.get(0).low(), 0.01);
        assertEquals(115.0, ifvgs.get(0).high(), 0.01);
        assertEquals(t3, ifvgs.get(0).ts());
    }

    @Test
    void bearFvgCloseThroughGapEmitsBullIfvg() {
        Instant t2 = BASE.plusSeconds(1800);
        Instant t3 = BASE.plusSeconds(2700);
        var bearFvg = new IctSnapshot.Zone("FVG", "bear", 112.0, 118.0, "fresh", t2);
        List<OhlcBar> bars = List.of(
                bar(BASE, 120, 122, 118, 119),
                bar(BASE.plusSeconds(900), 119, 120, 115, 116),
                bar(t2, 110, 112, 108, 111),
                bar(t3, 112, 123, 111, 122) // close above FVG high
        );

        List<IctSnapshot.Zone> ifvgs = IctEngine.deriveIfvgs(bars, List.of(bearFvg));

        assertEquals(1, ifvgs.size());
        assertEquals("IFVG", ifvgs.get(0).type());
        assertEquals("bull", ifvgs.get(0).direction());
        assertEquals("inverted", ifvgs.get(0).state());
    }

    @Test
    void selectEntryPrefersUnicornBreakerOverlappingFvg() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");
        var plainOb = new IctSnapshot.Zone("OB", "bull", ote.deep() - 1, ote.shallow() + 1, "fresh", BASE);
        var overlappingFvg = new IctSnapshot.Zone("FVG", "bull", ote.sweet() - 0.5, ote.sweet() + 0.5, "fresh", BASE);
        var unicornBreaker = new IctSnapshot.Zone("BREAKER", "bull",
                ote.sweet() - 0.5, ote.sweet() + 0.5, "fresh", BASE);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(plainOb), List.of(overlappingFvg), List.of(unicornBreaker), NONE, "long", ote);

        assertEquals(unicornBreaker, pick.zone());
        assertTrue(pick.oteOverlap());
        assertTrue(pick.unicorn());
    }

    @Test
    void intactObDoesNotEmitBreaker() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(900);
        var bullOb = new IctSnapshot.Zone("OB", "bull", 100.0, 105.0, "fresh", t0);
        List<OhlcBar> bars = List.of(
                bar(t0, 102, 105, 100, 101),
                bar(t1, 101, 104, 100.5, 103) // close stays inside OB
        );

        assertTrue(IctEngine.deriveBreakers(bars, List.of(bullOb)).isEmpty());
    }

    private static OhlcBar bar(Instant ts, double open, double high, double low, double close) {
        return new OhlcBar("XAUUSD", "M15", ts, ts, open, high, low, close, 100, ts);
    }
}
