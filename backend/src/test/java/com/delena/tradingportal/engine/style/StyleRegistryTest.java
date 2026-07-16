package com.delena.tradingportal.engine.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StyleRegistryTest {

    private final StyleRegistry registry = new StyleRegistry();

    @Test
    void scalpDayPositionalHaveDistinctThresholds() {
        StyleProfile scalp = registry.get(TradingStyle.SCALP);
        StyleProfile day = registry.get(TradingStyle.DAY);
        StyleProfile positional = registry.get(TradingStyle.POSITIONAL);

        assertEquals(0.4, scalp.ict().minFvgPts());
        assertEquals(0.8, day.ict().minFvgPts());
        assertEquals(1.5, positional.ict().minFvgPts());

        assertNotEquals(scalp.riskPct(), day.riskPct());
        assertNotEquals(day.riskPct(), positional.riskPct());

        assertEquals(25.0, scalp.maxSpreadPts());
        assertEquals(32.0, day.maxSpreadPts());
        assertEquals(40.0, positional.maxSpreadPts());
    }
}
