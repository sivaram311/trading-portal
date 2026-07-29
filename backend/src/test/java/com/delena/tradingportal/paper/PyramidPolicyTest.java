package com.delena.tradingportal.paper;

import com.delena.tradingportal.engine.style.StyleProfile;
import com.delena.tradingportal.engine.style.StyleRegistry;
import com.delena.tradingportal.engine.style.TradingStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PyramidPolicyTest {

    private final StyleRegistry registry = new StyleRegistry();
    private final StyleProfile scalp = registry.get(TradingStyle.SCALP);
    private final StyleProfile day = registry.get(TradingStyle.DAY);
    private final StyleProfile positional = registry.get(TradingStyle.POSITIONAL);

    @Test
    void rejectsWhenStyleIsMissing() {
        assertFalse(PyramidPolicy.canAddLeg("long", "long", "A+", 1.0, 1, null));
        assertEquals("NO_STYLE", PyramidPolicy.rejectionReason("long", "long", "A+", 1.0, 1, null));
    }

    @Test
    void rejectsOnDirectionMismatch() {
        assertFalse(PyramidPolicy.canAddLeg("long", "short", "A+", 1.0, 1, positional));
        assertEquals("DIRECTION_MISMATCH",
                PyramidPolicy.rejectionReason("long", "short", "A+", 1.0, 1, positional));
    }

    @Test
    void rejectsBelowGradeA() {
        assertFalse(PyramidPolicy.canAddLeg("long", "long", "B", 1.0, 1, positional));
        assertEquals("GRADE_BELOW_A",
                PyramidPolicy.rejectionReason("long", "long", "B", 1.0, 1, positional));
    }

    @Test
    void rejectsBelowMinUnrealizedR() {
        assertFalse(PyramidPolicy.canAddLeg("long", "long", "A", 0.79, 1, positional));
        assertEquals("UNREALIZED_R_BELOW_THRESHOLD",
                PyramidPolicy.rejectionReason("long", "long", "A", 0.79, 1, positional));
        assertTrue(PyramidPolicy.canAddLeg("long", "long", "A", PyramidPolicy.MIN_UNREALIZED_R, 1, positional));
    }

    @Test
    void scalpNeverAddsSinceMaxLegsIsOne() {
        assertFalse(PyramidPolicy.canAddLeg("long", "long", "A+", 5.0, 1, scalp));
        assertEquals("MAX_LEGS_REACHED",
                PyramidPolicy.rejectionReason("long", "long", "A+", 5.0, 1, scalp));
    }

    @Test
    void dayIsRiskCappedGivenFixedBasePaperSizing() {
        // DAY maxLegs=2 would allow a 2nd leg, but its total risk cap (0.625%) is below the
        // total after one add on the fixed 0.5% base leg (0.5 * (1 + 0.6) = 0.8%), so the risk
        // gate is the binding constraint on paper today.
        assertFalse(PyramidPolicy.canAddLeg("short", "short", "A+", 5.0, 1, day));
        assertEquals("RISK_CAP_EXCEEDED",
                PyramidPolicy.rejectionReason("short", "short", "A+", 5.0, 1, day));
    }

    @Test
    void positionalAllowsExactlyOneAddThenRiskCaps() {
        assertTrue(PyramidPolicy.canAddLeg("long", "long", "A", 0.8, 1, positional));
        // A second add on top of 2 legs would push total risk past the 0.875% cap.
        assertFalse(PyramidPolicy.canAddLeg("long", "long", "A", 2.0, 2, positional));
        assertEquals("RISK_CAP_EXCEEDED",
                PyramidPolicy.rejectionReason("long", "long", "A", 2.0, 2, positional));
    }

    @Test
    void rejectsInvalidLegCount() {
        assertEquals("INVALID_LEG_COUNT",
                PyramidPolicy.rejectionReason("long", "long", "A+", 5.0, 0, positional));
    }

    @Test
    void totalRiskAndAddLegRiskHelpersAreConsistent() {
        assertEquals(0.3, PyramidPolicy.addLegRiskPct(), 1e-9);
        assertEquals(0.8, PyramidPolicy.totalRiskPctAfterAdd(1), 1e-9);
    }
}
