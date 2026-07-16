package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;

/**
 * ICT Optimal Trade Entry (OTE) Fibonacci zone — 61.8%–79% retracement with 70.5% sweet spot
 * (docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md §2.3).
 */
public final class OteCalculator {

    private OteCalculator() {
    }

    public record OteZone(double deep, double sweet, double shallow, double invalidation) {
    }

    /**
     * @param swingStart impulse origin (low for long, high for short)
     * @param swingEnd   impulse terminus (high for long, low for short)
     * @param direction  {@code long} or {@code short}
     */
    public static OteZone computeOte(double swingStart, double swingEnd, String direction) {
        double range = Math.abs(swingEnd - swingStart);
        if ("LONG".equalsIgnoreCase(direction) || "long".equals(direction)) {
            double ote79 = swingEnd - 0.79 * range;
            double ote705 = swingEnd - 0.705 * range;
            double ote62 = swingEnd - 0.618 * range;
            return new OteZone(ote79, ote705, ote62, swingStart);
        }
        double ote79 = swingEnd + 0.79 * range;
        double ote705 = swingEnd + 0.705 * range;
        double ote62 = swingEnd + 0.618 * range;
        return new OteZone(ote79, ote705, ote62, swingStart);
    }

    /** Last completed impulse leg: low→high for long, high→low for short. */
    static ImpulseSwing deriveImpulseSwing(java.util.List<IctSnapshot.Swing> swings, String direction) {
        if (swings == null || swings.isEmpty() || "none".equals(direction)) {
            return null;
        }
        if ("long".equals(direction)) {
            IctSnapshot.Swing lastLow = null;
            IctSnapshot.Swing lastHighAfterLow = null;
            for (IctSnapshot.Swing s : swings) {
                if ("low".equals(s.type())) {
                    lastLow = s;
                    lastHighAfterLow = null;
                } else if ("high".equals(s.type()) && lastLow != null) {
                    lastHighAfterLow = s;
                }
            }
            if (lastLow != null && lastHighAfterLow != null) {
                return new ImpulseSwing(lastLow.price(), lastHighAfterLow.price());
            }
            return null;
        }
        if ("short".equals(direction)) {
            IctSnapshot.Swing lastHigh = null;
            IctSnapshot.Swing lastLowAfterHigh = null;
            for (IctSnapshot.Swing s : swings) {
                if ("high".equals(s.type())) {
                    lastHigh = s;
                    lastLowAfterHigh = null;
                } else if ("low".equals(s.type()) && lastHigh != null) {
                    lastLowAfterHigh = s;
                }
            }
            if (lastHigh != null && lastLowAfterHigh != null) {
                return new ImpulseSwing(lastHigh.price(), lastLowAfterHigh.price());
            }
        }
        return null;
    }

    record ImpulseSwing(double start, double end) {
    }

    static boolean overlapsOte(IctSnapshot.Zone zone, OteZone ote) {
        double oteLow = Math.min(ote.deep(), ote.shallow());
        double oteHigh = Math.max(ote.deep(), ote.shallow());
        return zone.low() <= oteHigh && zone.high() >= oteLow;
    }

    static boolean nearSweetSpot(IctSnapshot.Zone zone, OteZone ote, double eps) {
        double mid = (zone.low() + zone.high()) / 2.0;
        return Math.abs(mid - ote.sweet()) <= eps;
    }

    static IctSnapshot.OteZone toSnapshotOte(OteZone ote) {
        if (ote == null) {
            return null;
        }
        return new IctSnapshot.OteZone(ote.deep(), ote.sweet(), ote.shallow(), ote.invalidation());
    }
}
