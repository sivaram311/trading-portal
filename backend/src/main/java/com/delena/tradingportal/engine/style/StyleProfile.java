package com.delena.tradingportal.engine.style;

import com.delena.tradingportal.engine.gann.GannConfig;
import com.delena.tradingportal.engine.ict.IctConfig;

import java.time.Duration;

/**
 * Preset thresholds for a {@link TradingStyle}. Pipeline applies {@code ict}/{@code gann};
 * {@link com.delena.tradingportal.engine.risk.MarketQualityGate} uses {@code maxSpreadPts};
 * {@link com.delena.tradingportal.paper.PositionManager} uses {@code beTriggerR}/{@code scaleOutPct}/{@code maxHold}.
 * {@code riskPct} is still profile metadata for initial sizing — paper RiskGate enforces a fixed
 * 0.5% per trade and max 1 open <em>position</em> regardless of style. {@code maxLegs} IS enforced
 * on the paper path for ADD_LEG (limited pyramiding, DEEP-ALGORITHMS §7): SCALP=1, DAY=2,
 * POSITIONAL=3 legs on the one open position, gated by
 * {@link com.delena.tradingportal.paper.PyramidPolicy}. Adding legs never raises the open
 * position count past 1.
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
