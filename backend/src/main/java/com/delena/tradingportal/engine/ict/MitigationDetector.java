package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ICT Mitigation Block detection (docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md §2.8,
 * docs/theory/ICT-GOLD.md §6). A Mitigation Block is the first return into an Order Block after
 * the displacement leg that created it, where the return only partially fills the zone (no full
 * close-through invalidation) and is followed by a reaction — rejection wick, engulfing candle, or
 * FVG confluence inside the zone — that resumes price in the OB's original direction.
 */
final class MitigationDetector {

    /** Minimum wick fraction of bar range to count as a rejection wick. */
    private static final double REJECTION_WICK_FRAC = 0.35;

    private MitigationDetector() {
    }

    /**
     * @param bars M15 bars covering the same window used to derive {@code obs}
     * @param obs  order block zones (type {@code OB}) from {@link IctEngine#deriveOrderBlocks}
     * @return one {@code MITIGATION} zone per OB that mitigated on its first valid touch
     */
    static List<IctSnapshot.Zone> deriveMitigations(List<OhlcBar> bars, List<IctSnapshot.Zone> obs) {
        return deriveMitigations(bars, obs, List.of());
    }

    /**
     * Overload accepting FVG zones so a touch candle without a rejection wick or engulfing pattern
     * can still confirm its reaction via FVG confluence inside the OB (DEEP-ALGORITHMS §2.8).
     */
    static List<IctSnapshot.Zone> deriveMitigations(List<OhlcBar> bars, List<IctSnapshot.Zone> obs,
                                                     List<IctSnapshot.Zone> fvgs) {
        List<IctSnapshot.Zone> out = new ArrayList<>();
        if (bars == null || bars.isEmpty() || obs == null || obs.isEmpty()) {
            return out;
        }
        List<IctSnapshot.Zone> fvgZones = fvgs == null ? List.of() : fvgs;
        for (IctSnapshot.Zone ob : obs) {
            if (!"OB".equals(ob.type())) {
                continue;
            }
            IctSnapshot.Zone mitigation = detectOne(bars, ob, fvgZones);
            if (mitigation != null) {
                out.add(mitigation);
            }
        }
        return out;
    }

    private static IctSnapshot.Zone detectOne(List<OhlcBar> bars, IctSnapshot.Zone ob, List<IctSnapshot.Zone> fvgs) {
        boolean bull = "bull".equals(ob.direction());
        boolean bear = "bear".equals(ob.direction());
        if (!bull && !bear) {
            return null;
        }
        int obIdx = barIndex(bars, ob.ts());
        if (obIdx < 0 || obIdx >= bars.size() - 1) {
            return null;
        }

        // Scan every bar after the OB candle itself (never the OB candle, whose own range trivially
        // equals the zone) for the first wick/body touch, then require a reaction before confirming
        // continuation. Touch, reaction and continuation may all land on the same candle (a single
        // elongated bar that wicks into the OB and closes back out) or span several bars.
        int touchIdx = -1;
        boolean reactionConfirmed = false;
        for (int i = obIdx + 1; i < bars.size(); i++) {
            OhlcBar b = bars.get(i);
            // A full close-through invalidates the OB (§2.6 Breaker) before mitigation completes.
            if (invalidated(b, bull, ob)) {
                return null;
            }
            if (touchIdx < 0) {
                if (!overlaps(b, ob)) {
                    continue;
                }
                touchIdx = i;
            }
            if (!reactionConfirmed) {
                reactionConfirmed = hasRejectionWick(b, bull)
                        || isEngulfing(bars.get(i - 1), b, bull)
                        || overlapsFvg(ob, fvgs, bull);
            }
            if (reactionConfirmed && continuation(b, bull, ob)) {
                return new IctSnapshot.Zone("MITIGATION", bull ? "bull" : "bear",
                        ob.low(), ob.high(), "mitigated", bars.get(touchIdx).ts());
            }
        }
        return null;
    }

    private static boolean invalidated(OhlcBar b, boolean bull, IctSnapshot.Zone ob) {
        return bull ? b.close() < ob.low() : b.close() > ob.high();
    }

    private static boolean overlaps(OhlcBar b, IctSnapshot.Zone ob) {
        return b.low() <= ob.high() && b.high() >= ob.low();
    }

    private static boolean continuation(OhlcBar b, boolean bull, IctSnapshot.Zone ob) {
        return bull ? b.close() > ob.high() : b.close() < ob.low();
    }

    private static boolean hasRejectionWick(OhlcBar b, boolean bull) {
        double range = b.high() - b.low();
        if (range <= 0) {
            return false;
        }
        if (bull) {
            double lowerWick = Math.min(b.open(), b.close()) - b.low();
            return lowerWick / range >= REJECTION_WICK_FRAC && b.close() > b.open();
        }
        double upperWick = b.high() - Math.max(b.open(), b.close());
        return upperWick / range >= REJECTION_WICK_FRAC && b.close() < b.open();
    }

    private static boolean isEngulfing(OhlcBar prev, OhlcBar cur, boolean bull) {
        if (bull) {
            return cur.close() > cur.open() && prev.close() < prev.open()
                    && cur.close() > prev.open() && cur.open() < prev.close();
        }
        return cur.close() < cur.open() && prev.close() > prev.open()
                && cur.close() < prev.open() && cur.open() > prev.close();
    }

    private static boolean overlapsFvg(IctSnapshot.Zone ob, List<IctSnapshot.Zone> fvgs, boolean bull) {
        String want = bull ? "bull" : "bear";
        for (IctSnapshot.Zone fvg : fvgs) {
            if (!"FVG".equals(fvg.type()) || !want.equals(fvg.direction())) {
                continue;
            }
            if (ob.low() <= fvg.high() && fvg.low() <= ob.high()) {
                return true;
            }
        }
        return false;
    }

    private static int barIndex(List<OhlcBar> bars, Instant ts) {
        for (int i = 0; i < bars.size(); i++) {
            if (bars.get(i).ts().equals(ts)) {
                return i;
            }
        }
        return -1;
    }
}
