package com.delena.tradingportal.engine.style;

import com.delena.tradingportal.engine.gann.GannConfig;
import com.delena.tradingportal.engine.ict.IctConfig;

import java.time.Duration;

/**
 * Preset thresholds for a {@link TradingStyle}. Pipeline applies {@code ict}/{@code gann};
 * {@link com.delena.tradingportal.engine.risk.MarketQualityGate} uses {@code maxSpreadPts};
 * {@link com.delena.tradingportal.paper.PositionManager} uses {@code beTriggerR}/{@code scaleOutPct}/{@code maxHold}.
 * {@code riskPct}/{@code maxLegs} are profile metadata — paper RiskGate still enforces 0.5% and max 1 open.
 */
public record StyleProfile(
        IctConfig ict,
        GannConfig gann,
        double riskPct,
        int maxLegs,
        Duration maxHold,
        boolean requireKillzone,
        double beTriggerR,
        double scaleOutPct,
        double maxSpreadPts
) {}
