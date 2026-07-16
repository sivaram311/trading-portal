package com.delena.tradingportal.engine.risk;

import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.model.RiskVerdict;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Market quality veto (docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md §6.4). Evaluated after
 * confluence; {@link com.delena.tradingportal.pipeline.PipelineService} merges deny reasons into
 * {@link RiskVerdict} so RiskGate stays unchanged.
 */
@Component
public class MarketQualityGate {

    public static final String QUALITY_SPREAD = "QUALITY_SPREAD";
    public static final String QUALITY_ATR_EXTREME = "QUALITY_ATR_EXTREME";
    public static final String QUALITY_GAP = "QUALITY_GAP";
    public static final String QUALITY_DUPLICATE = "QUALITY_DUPLICATE";
    public static final String QUALITY_DATA = "QUALITY_DATA";

    private static final double DEFAULT_SCALP_SPREAD = 25.0;
    private static final double DEFAULT_DAY_SPREAD = 40.0;
    private static final double DEFAULT_POSITIONAL_SPREAD = 50.0;

    private final Duration duplicateWindow;
    private final double atrRatioExtreme;
    private final double gapAtrMultiplier;
    private final int atrPeriodShort;
    private final int atrPeriodLong;

    public MarketQualityGate() {
        this(Duration.ofMinutes(30), 2.0, 1.5, 14, 50);
    }

    MarketQualityGate(Duration duplicateWindow, double atrRatioExtreme, double gapAtrMultiplier,
                      int atrPeriodShort, int atrPeriodLong) {
        this.duplicateWindow = duplicateWindow;
        this.atrRatioExtreme = atrRatioExtreme;
        this.gapAtrMultiplier = gapAtrMultiplier;
        this.atrPeriodShort = atrPeriodShort;
        this.atrPeriodLong = atrPeriodLong;
    }

    public record QualityContext(
            Instant decisionTs,
            double spreadPts,
            double atrCurrent,
            double atrLong,
            Double gapPts,
            Instant lastSameDirDecisionTs,
            String direction,
            double maxSpreadPts
    ) {}

    /** Returns veto reason codes; empty list means market quality passes. */
    public List<String> evaluate(QualityContext ctx) {
        return denyReasons(ctx);
    }

    public List<String> denyReasons(QualityContext ctx) {
        List<String> denies = new ArrayList<>();

        if (ctx.atrLong() <= 0 || ctx.atrCurrent() <= 0) {
            denies.add(QUALITY_DATA);
            return denies;
        }

        if (ctx.spreadPts() > ctx.maxSpreadPts()) {
            denies.add(QUALITY_SPREAD);
        }

        if (ctx.atrCurrent() / ctx.atrLong() > atrRatioExtreme) {
            denies.add(QUALITY_ATR_EXTREME);
        }

        if (ctx.gapPts() != null && Math.abs(ctx.gapPts()) > gapAtrMultiplier * ctx.atrCurrent()) {
            denies.add(QUALITY_GAP);
        }

        if (isDuplicate(ctx)) {
            denies.add(QUALITY_DUPLICATE);
        }

        return denies;
    }

    /** Prefer {@link StyleProfile#maxSpreadPts()} when present; else style constructor defaults. */
    public double resolveMaxSpreadPts(Optional<Double> styleSpread, TradingStyle style) {
        if (styleSpread.isPresent()) {
            return styleSpread.get();
        }
        return switch (style != null ? style : TradingStyle.DAY) {
            case SCALP -> DEFAULT_SCALP_SPREAD;
            case DAY -> DEFAULT_DAY_SPREAD;
            case POSITIONAL -> DEFAULT_POSITIONAL_SPREAD;
        };
    }

    /**
     * Build evaluation context from stored OHLC. Spread uses the latest M5 range as a paper-path
     * proxy (no live quote feed).
     */
    public QualityContext contextFromBars(List<OhlcBar> m5, Instant decisionTs, String direction,
                                          Instant lastSameDirDecisionTs, Optional<Double> styleMaxSpread,
                                          TradingStyle style) {
        double maxSpread = resolveMaxSpreadPts(styleMaxSpread, style);
        return new QualityContext(
                decisionTs,
                spreadProxy(m5),
                atr(m5, atrPeriodShort),
                atr(m5, atrPeriodLong),
                gapPts(m5),
                lastSameDirDecisionTs,
                direction,
                maxSpread);
    }

    /** Merge quality vetoes into an existing risk verdict (clears size when vetoed). */
    public static RiskVerdict mergeIntoVerdict(RiskVerdict risk, List<String> qualityDenies) {
        if (qualityDenies.isEmpty()) {
            return risk;
        }
        List<String> merged = new ArrayList<>(risk.denyReasons());
        merged.addAll(qualityDenies);
        return new RiskVerdict(risk.decisionId(), risk.ts(), false, null, null,
                risk.dailyLossR(), risk.openPositions(), merged, risk.checks());
    }

    private boolean isDuplicate(QualityContext ctx) {
        String dir = ctx.direction();
        if (dir == null || dir.isBlank() || "flat".equalsIgnoreCase(dir)) {
            return false;
        }
        Instant last = ctx.lastSameDirDecisionTs();
        if (last == null) {
            return false;
        }
        return Duration.between(last, ctx.decisionTs()).compareTo(duplicateWindow) < 0;
    }

    static double spreadProxy(List<OhlcBar> bars) {
        if (bars.isEmpty()) {
            return 0;
        }
        OhlcBar last = bars.get(bars.size() - 1);
        return Math.max(0, last.high() - last.low());
    }

    static Double gapPts(List<OhlcBar> bars) {
        if (bars.size() < 2) {
            return null;
        }
        OhlcBar last = bars.get(bars.size() - 1);
        OhlcBar prev = bars.get(bars.size() - 2);
        return last.open() - prev.close();
    }

    static double atr(List<OhlcBar> bars, int period) {
        if (bars.size() < 2) {
            return 0;
        }
        int n = Math.min(period, bars.size() - 1);
        double sum = 0;
        for (int i = bars.size() - n; i < bars.size(); i++) {
            OhlcBar cur = bars.get(i);
            OhlcBar prev = bars.get(i - 1);
            double tr = Math.max(cur.high() - cur.low(),
                    Math.max(Math.abs(cur.high() - prev.close()), Math.abs(cur.low() - prev.close())));
            sum += tr;
        }
        return sum / n;
    }
}
