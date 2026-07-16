package com.delena.tradingportal.engine;

import com.delena.tradingportal.engine.confluence.ConfluenceEngine;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.GannSnapshot;
import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.RiskVerdict;
import com.delena.tradingportal.paper.PaperDecisionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfluenceEngineTest {

    private final ConfluenceEngine engine = new ConfluenceEngine();
    // NY 08:30 EDT — inside NY_OPEN killzone, not midday.
    private final Instant asof = Instant.parse("2026-07-15T12:30:00Z");

    @Test
    void conflictDirectionYieldsFailClosedNonConfirmableDecision() {
        IctSnapshot ictShort = ictShortReversal();
        GannSnapshot gannLong = gannFadeLong(); // long side -> conflicts with ICT short

        ConfluenceDecision d = engine.decide(ictShort, gannLong, asof, false, "v1");

        assertEquals("CONFLICT", d.agreement());
        assertEquals("NONE", d.mode());
        assertEquals("F", d.grade());
        assertEquals("flat", d.direction());
        assertEquals("deny", d.automation());
        assertTrue(d.invalidIf().isEmpty(), "NONE mode carries no invalidation");

        // Q1 gate: a CONFLICT decision must never be confirmable into a paper fill,
        // even if a (stale) risk verdict claimed ok.
        RiskVerdict fakeOkRisk = new RiskVerdict(d.id(), asof, true, 1.0, 0.5, 0, 0,
                List.of(), new RiskVerdict.Checks(true, true, true, true, true, true, true));
        assertFalse(PaperDecisionPolicy.isConfirmable(d, fakeOkRisk),
                "conflict/grade-F/deny must not be confirmable");
    }

    @Test
    void alignedReversalIsGradedAndConfirmable() {
        ConfluenceDecision d = engine.decide(ictShortReversal(), gannFadeShort(), asof, false, "v1");

        assertEquals("ALIGN_SHORT", d.agreement());
        assertEquals("short", d.direction());
        assertEquals("R", d.mode());
        assertTrue(List.of("A+", "A").contains(d.grade()), "expected high grade, got " + d.grade());
        assertEquals("confirm", d.automation());
        assertFalse(d.invalidIf().isEmpty(), "actionable mode must carry mandatory invalidation");
    }

    @Test
    void newsVetoYieldsFailClosedDeny() {
        ConfluenceDecision d = engine.decide(ictShortReversal(), gannFadeShort(), asof, true, "v1");

        assertEquals("NONE", d.mode());
        assertEquals("F", d.grade());
        assertEquals("flat", d.direction());
        assertEquals("deny", d.automation());
        assertTrue(d.reasons().contains("NEWS_VETO"));
        RiskVerdict fakeOkRisk = new RiskVerdict(d.id(), asof, true, 1.0, 0.5, 0, 0,
                List.of(), new RiskVerdict.Checks(true, true, true, true, true, true, true));
        assertFalse(PaperDecisionPolicy.isConfirmable(d, fakeOkRisk));
    }

    // ---- fixtures ----------------------------------------------------------

    private IctSnapshot ictShortReversal() {
        var htf = new IctSnapshot.Htf(1980, 2010, 1995, "premium", "long");
        var structure = new IctSnapshot.Structure(List.of(), "MSS", "short", true);
        var liquidity = new IctSnapshot.Liquidity(
                List.of(new IctSnapshot.Pool("AHH", 2008, "high")), "sweep", "AHH", true);
        var entry = new IctSnapshot.Zone("OB", "bear", 2006, 2009, "fresh", asof);
        var zones = new IctSnapshot.Zones(List.of(), List.of(), entry, null);
        return new IctSnapshot("XAUUSD", asof, "NY_OPEN", htf, structure, liquidity, zones, 5,
                List.of("KZ_NY_OPEN", "SWEEP_AHH", "MSS_BEAR", "DISP_OK", "OB_ACTIVE", "PD_PREMIUM"),
                new IctSnapshot.RawRefs(List.of()));
    }

    private GannSnapshot gannFadeShort() {
        var angle = new GannSnapshot.Angle(1.0, 1998, 8, 4.0, "over_up", true,
                new GannSnapshot.Fan(1998, 2005, 1996));
        var so9 = new GannSnapshot.So9(List.of(), true, null);
        var timeSquare = new GannSnapshot.TimeSquare(45, 8, List.of(), true);
        var cycles = new GannSnapshot.Cycles(0.08, "CYC_1_8");
        return new GannSnapshot("XAUUSD", asof, new GannSnapshot.Pivot("NY_OPEN", 2000, asof),
                angle, so9, timeSquare, cycles, "NY_OPEN", "fade_short", 4,
                List.of("ANG_OVER_UP", "ANG_ALERT", "SO9_FINE", "TSQ_45"),
                new GannSnapshot.Filters(true, false, null));
    }

    private GannSnapshot gannFadeLong() {
        var angle = new GannSnapshot.Angle(1.0, 2005, -8, -4.0, "over_down", true,
                new GannSnapshot.Fan(2005, 2010, 2002));
        var so9 = new GannSnapshot.So9(List.of(), true, null);
        var timeSquare = new GannSnapshot.TimeSquare(45, 8, List.of(), true);
        var cycles = new GannSnapshot.Cycles(0.08, "CYC_1_8");
        return new GannSnapshot("XAUUSD", asof, new GannSnapshot.Pivot("NY_OPEN", 2000, asof),
                angle, so9, timeSquare, cycles, "NY_OPEN", "fade_long", 4,
                List.of("ANG_OVER_DOWN", "ANG_ALERT", "SO9_FINE", "TSQ_45"),
                new GannSnapshot.Filters(false, true, null));
    }
}
