package com.delena.tradingportal.backtest;

import com.delena.tradingportal.engine.style.StyleProfile;
import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.model.Entry;
import com.delena.tradingportal.model.OhlcBar;

/**
 * Realistic gold fill model (DEEP-ALGORITHMS §9.3): half-spread + optional slippage;
 * spread veto when above style max; DAY/POSITIONAL fill when the bar trades into the entry zone.
 */
public final class FillSimulator {

    private FillSimulator() {
    }

    public record FillResult(boolean filled, double fillPrice, String rejectReason) {
        public static FillResult reject(String reason) {
            return new FillResult(false, 0.0, reason);
        }

        public static FillResult fill(double price) {
            return new FillResult(true, price, null);
        }
    }

    public static FillResult simulate(Entry entry, String direction, OhlcBar bar, double spreadPts,
                                      double slippagePts, TradingStyle style, StyleProfile profile) {
        if (entry == null || bar == null || direction == null) {
            return FillResult.reject("MISSING_INPUT");
        }
        if (spreadPts > profile.maxSpreadPts()) {
            return FillResult.reject("SPREAD_TOO_WIDE");
        }
        if ("flat".equalsIgnoreCase(direction)) {
            return FillResult.reject("FLAT_DIRECTION");
        }

        boolean longDir = "long".equalsIgnoreCase(direction);
        double zoneLow = Math.min(entry.low(), entry.high());
        double zoneHigh = Math.max(entry.low(), entry.high());
        if (zoneHigh <= zoneLow) {
            return FillResult.reject("INVALID_ZONE");
        }

        boolean zoneTouched = bar.low() <= zoneHigh && bar.high() >= zoneLow;
        if (!zoneTouched) {
            return FillResult.reject("ZONE_NOT_TOUCHED");
        }

        if (style == TradingStyle.SCALP) {
            boolean bullishClose = bar.close() >= bar.open();
            if (longDir && !bullishClose) {
                return FillResult.reject("SCALP_NO_BULLISH_CLOSE");
            }
            if (!longDir && bullishClose) {
                return FillResult.reject("SCALP_NO_BEARISH_CLOSE");
            }
        }

        double base = clamp(longDir ? zoneHigh : zoneLow, bar.low(), bar.high());
        double halfSpread = spreadPts / 2.0;
        double fill = longDir ? base + halfSpread + slippagePts : base - halfSpread - slippagePts;
        return FillResult.fill(round(fill));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
