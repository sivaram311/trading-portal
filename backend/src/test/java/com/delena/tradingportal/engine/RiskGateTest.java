package com.delena.tradingportal.engine;

import com.delena.tradingportal.engine.confluence.ConfluenceEngine;
import com.delena.tradingportal.engine.risk.RiskGate;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.Entry;
import com.delena.tradingportal.model.GannSnapshot;
import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.RiskVerdict;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskGateTest {

    private final RiskGate gate = new RiskGate();
    private final ConfluenceEngine confluence = new ConfluenceEngine();
    private final Instant asof = Instant.parse("2026-07-15T12:30:00Z");

    @Test
    void confluenceDenyIsNeverOk() {
        // A CONFLICT decision from the engine has automation=deny.
        ConfluenceDecision conflict = confluence.decide(ictShort(), gannLong(), asof, false, "v1");
        RiskVerdict v = gate.verdict(conflict, 0, 0.0);
        assertFalse(v.ok());
        assertTrue(v.denyReasons().contains("RISK_CONFLUENCE_DENY"),
                () -> "expected RISK_CONFLUENCE_DENY, got " + v.denyReasons());
    }

    @Test
    void maxOpenPositionsDeniesSecondEntry() {
        ConfluenceDecision aligned = alignedA();
        assertTrue(gate.verdict(aligned, 0, 0.0).ok(), "first entry should pass risk");

        RiskVerdict blocked = gate.verdict(aligned, 1, 0.0);
        assertFalse(blocked.ok());
        assertTrue(blocked.denyReasons().contains("RISK_MAX_OPEN_POSITIONS"));
        assertFalse(blocked.checks().maxOpenPositions());
    }

    @Test
    void dailyLossHaltDeniesNewEntries() {
        RiskVerdict halted = gate.verdict(alignedA(), 0, -2.0);
        assertFalse(halted.ok());
        assertTrue(halted.denyReasons().contains("RISK_MAX_DAILY_LOSS"));
    }

    private ConfluenceDecision alignedA() {
        return new ConfluenceDecision(UUID.randomUUID().toString(), "XAUUSD", asof, "R", "short", "A",
                6.0, "ALIGN_SHORT", List.of("ALIGN_SHORT", "ICT_MSS_BEAR"),
                new Entry("OB", 2006, 2009), 2013.0, List.of(1995.0, 1980.0),
                List.of("close_above:2013", "news_blackout"),
                new ConfluenceDecision.Engines("a", "b"), "confirm", "v1");
    }

    private IctSnapshot ictShort() {
        var htf = new IctSnapshot.Htf(1980, 2010, 1995, "premium", "long");
        var structure = new IctSnapshot.Structure(List.of(), "MSS", "short", true);
        var liquidity = new IctSnapshot.Liquidity(
                List.of(new IctSnapshot.Pool("AHH", 2008, "high")), "sweep", "AHH", true);
        var zones = new IctSnapshot.Zones(List.of(), List.of(),
                new IctSnapshot.Zone("OB", "bear", 2006, 2009, "fresh", asof));
        return new IctSnapshot("XAUUSD", asof, "NY_OPEN", htf, structure, liquidity, zones, 5,
                List.of("MSS_BEAR"), new IctSnapshot.RawRefs(List.of()));
    }

    private GannSnapshot gannLong() {
        var angle = new GannSnapshot.Angle(1.0, 2005, -8, -4.0, "over_down", true,
                new GannSnapshot.Fan(2005, 2010, 2002));
        return new GannSnapshot("XAUUSD", asof, new GannSnapshot.Pivot("NY_OPEN", 2000, asof),
                angle, new GannSnapshot.So9(List.of(), true, null),
                new GannSnapshot.TimeSquare(45, 8, List.of(), true),
                new GannSnapshot.Cycles(0.08, "CYC_1_8"), "NY_OPEN", "fade_long", 4,
                List.of("ANG_OVER_DOWN"), new GannSnapshot.Filters(false, true, null));
    }
}
