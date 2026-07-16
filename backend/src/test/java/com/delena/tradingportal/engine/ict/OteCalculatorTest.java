package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OteCalculatorTest {

    private static final Instant TS = Instant.parse("2026-07-15T12:30:00Z");
    private static final List<IctSnapshot.Zone> NO_EXTRA = List.of();

    @Test
    void longOteLevelsMatchFibonacciRetracement() {
        // Impulse 1900 (low) → 2000 (high), range 100
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");

        assertEquals(1921.0, ote.deep(), 0.01);      // 2000 - 79
        assertEquals(1929.5, ote.sweet(), 0.01);     // 2000 - 70.5
        assertEquals(1938.2, ote.shallow(), 0.01);   // 2000 - 61.8
        assertEquals(1900.0, ote.invalidation(), 0.01);
        assertTrue(ote.deep() < ote.sweet() && ote.sweet() < ote.shallow());
    }

    @Test
    void shortOteLevelsMatchFibonacciRetracement() {
        // Impulse 2000 (high) → 1900 (low), range 100
        OteCalculator.OteZone ote = OteCalculator.computeOte(2000, 1900, "short");

        assertEquals(1979.0, ote.deep(), 0.01);      // 1900 + 79
        assertEquals(1970.5, ote.sweet(), 0.01);     // 1900 + 70.5
        assertEquals(1961.8, ote.shallow(), 0.01);   // 1900 + 61.8
        assertEquals(2000.0, ote.invalidation(), 0.01);
        assertTrue(ote.shallow() < ote.sweet() && ote.sweet() < ote.deep());
    }

    @Test
    void deriveImpulseSwingFindsLastLowToHighForLong() {
        var swings = List.of(
                swing("low", 1900),
                swing("high", 1950),
                swing("low", 1920),
                swing("high", 2000)
        );

        OteCalculator.ImpulseSwing impulse = OteCalculator.deriveImpulseSwing(swings, "long");

        assertNotNull(impulse);
        assertEquals(1920, impulse.start(), 0.01);
        assertEquals(2000, impulse.end(), 0.01);
    }

    @Test
    void deriveImpulseSwingFindsLastHighToLowForShort() {
        var swings = List.of(
                swing("high", 2000),
                swing("low", 1950),
                swing("high", 1980),
                swing("low", 1900)
        );

        OteCalculator.ImpulseSwing impulse = OteCalculator.deriveImpulseSwing(swings, "short");

        assertNotNull(impulse);
        assertEquals(1980, impulse.start(), 0.01);
        assertEquals(1900, impulse.end(), 0.01);
    }

    @Test
    void selectEntryPrefersObOverlappingOteOverNonOverlappingOb() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");

        var outsideOb = zone("OB", "bull", 1880, 1895);
        var oteOb = zone("OB", "bull", ote.deep() - 1, ote.shallow() + 1);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(outsideOb, oteOb), List.of(), NO_EXTRA, NO_EXTRA, "long", ote);

        assertEquals(oteOb, pick.zone());
        assertTrue(pick.oteOverlap());
    }

    @Test
    void selectEntryPrefersOteOverlappingFvgWhenNoObOverlaps() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");

        var outsideOb = zone("OB", "bull", 1880, 1890);
        var oteFvg = zone("FVG", "bull", ote.sweet() - 0.5, ote.sweet() + 0.5);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(outsideOb), List.of(oteFvg), NO_EXTRA, NO_EXTRA, "long", ote);

        assertEquals(oteFvg, pick.zone());
        assertTrue(pick.oteOverlap());
    }

    @Test
    void selectEntryScoresSweetSpotHigherAmongOteOverlaps() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");

        var shallowOverlap = zone("OB", "bull", ote.shallow() - 0.5, ote.shallow() + 0.5);
        var sweetOverlap = zone("OB", "bull", ote.sweet() - 0.4, ote.sweet() + 0.4);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(shallowOverlap, sweetOverlap), List.of(), NO_EXTRA, NO_EXTRA, "long", ote);

        assertEquals(sweetOverlap, pick.zone());
        assertTrue(pick.oteOverlap());
    }

    @Test
    void selectEntryFallsBackToObFirstWhenNoOte() {
        var ob = zone("OB", "bull", 1930, 1935);
        var fvg = zone("FVG", "bull", 1940, 1945);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(ob), List.of(fvg), NO_EXTRA, NO_EXTRA, "long", null);

        assertEquals(ob, pick.zone());
        assertFalse(pick.oteOverlap());
    }

    @Test
    void selectEntryFallsBackWhenOtePresentButNoPositiveScore() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");

        var farOb = new IctSnapshot.Zone("OB", "bull", 1800, 1810, "tested", TS);
        var farFvg = new IctSnapshot.Zone("FVG", "bull", 1815, 1820, "tested", TS);

        IctEngine.EntrySelection pick = IctEngine.selectEntry(
                List.of(farOb), List.of(farFvg), NO_EXTRA, NO_EXTRA, "long", ote);

        assertEquals(farOb, pick.zone());
        assertFalse(pick.oteOverlap());
    }

    @Test
    void overlapsOteDetectsPartialIntersection() {
        OteCalculator.OteZone ote = OteCalculator.computeOte(1900, 2000, "long");
        var partial = zone("OB", "bull", ote.shallow() - 2, ote.shallow() + 5);

        assertTrue(OteCalculator.overlapsOte(partial, ote));

        var disjoint = zone("OB", "bull", 1800, 1810);
        assertFalse(OteCalculator.overlapsOte(disjoint, ote));
    }

    @Test
    void toSnapshotOteMapsFields() {
        OteCalculator.OteZone calc = OteCalculator.computeOte(1900, 2000, "long");
        IctSnapshot.OteZone snap = OteCalculator.toSnapshotOte(calc);

        assertNotNull(snap);
        assertEquals(calc.deep(), snap.deep(), 0.01);
        assertEquals(calc.sweet(), snap.sweet(), 0.01);
        assertEquals(calc.shallow(), snap.shallow(), 0.01);
        assertEquals(calc.invalidation(), snap.invalidation(), 0.01);
        assertNull(OteCalculator.toSnapshotOte(null));
    }

    private static IctSnapshot.Swing swing(String type, double price) {
        return new IctSnapshot.Swing(type, price, TS, 0);
    }

    private static IctSnapshot.Zone zone(String type, String direction, double low, double high) {
        return new IctSnapshot.Zone(type, direction, low, high, "fresh", TS);
    }
}
