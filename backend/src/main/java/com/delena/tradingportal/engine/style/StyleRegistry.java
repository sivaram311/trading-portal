package com.delena.tradingportal.engine.style;

import com.delena.tradingportal.engine.gann.GannConfig;
import com.delena.tradingportal.engine.ict.IctConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Preset style profiles per DEEP-ALGORITHMS-AND-CALCULATIONS.md §8.
 */
@Component
public class StyleRegistry {

    private final Map<TradingStyle, StyleProfile> profiles;

    public StyleRegistry() {
        var map = new EnumMap<TradingStyle, StyleProfile>(TradingStyle.class);
        map.put(TradingStyle.SCALP, scalp());
        map.put(TradingStyle.DAY, day());
        map.put(TradingStyle.POSITIONAL, positional());
        this.profiles = Map.copyOf(map);
    }

    public StyleProfile get(TradingStyle style) {
        return profiles.getOrDefault(style, profiles.get(TradingStyle.DAY));
    }

    private static StyleProfile scalp() {
        return new StyleProfile(
                ictWithMinFvgPts(0.4),
                gannWithAtrAlert(1.0),
                0.4,
                1,
                Duration.ofMinutes(45),
                true,
                0.75,
                0.50,
                25.0);
    }

    private static StyleProfile day() {
        GannConfig gann = GannConfig.defaults();
        IctConfig ict = IctConfig.defaults();
        return new StyleProfile(
                ict,
                gann,
                0.625,
                2,
                Duration.ofHours(8),
                false,
                1.0,
                0.45,
                32.0);
    }

    private static StyleProfile positional() {
        return new StyleProfile(
                ictWithMinFvgPts(1.5),
                gannWithAtrAlert(1.5),
                0.875,
                3,
                Duration.ofDays(5),
                false,
                1.0,
                0.40,
                40.0);
    }

    private static IctConfig ictWithMinFvgPts(double minFvgPts) {
        IctConfig d = IctConfig.defaults();
        return new IctConfig(
                d.swingNM15(),
                d.swingNH1(),
                d.equalEpsPts(),
                d.sweepReclaimBars(),
                d.minFvgAtrFrac(),
                minFvgPts,
                d.displacementBodyFrac(),
                d.atrPeriod());
    }

    private static GannConfig gannWithAtrAlert(double atrAlert) {
        GannConfig d = GannConfig.defaults();
        return new GannConfig(
                d.atrPeriod(),
                atrAlert,
                d.timeScale(),
                d.nearTimeMin(),
                d.so9NearPct(),
                d.so9NearPts(),
                d.so9FineSteps(),
                d.so9OddNMax(),
                d.milestonesMin(),
                d.sessionLenMin(),
                d.cycleFractions(),
                d.volSpikeMult(),
                d.entryTfMinutes());
    }
}
