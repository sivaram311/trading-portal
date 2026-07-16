package com.delena.tradingportal.backtest;

import com.delena.tradingportal.engine.style.TradingStyle;

/**
 * Tunables for a bar-by-bar paper backtest (DEEP-ALGORITHMS §9). Walk-forward / Monte-Carlo deferred.
 */
public record BacktestConfig(
        TradingStyle style,
        /** Minimum M15 bars before the walk loop starts. */
        int lookbackBars,
        /** Simulated bid/ask spread in price points (XAUUSD). */
        double spreadPts,
        /** Extra adverse slippage applied on fill (deterministic; 0 = none). */
        double slippagePts,
        /** Confluence weights version passed to {@link com.delena.tradingportal.engine.confluence.ConfluenceEngine}. */
        String weightsVersion,
        /** When true, every bar is evaluated with a news veto (fail-closed). */
        boolean newsVeto,
        /** Bars after signal to attempt zone fill (1 = next bar only, per §9.2). */
        int fillValidityBars
) {
    public static BacktestConfig defaults(TradingStyle style) {
        return new BacktestConfig(style, 50, 2.0, 0.0, "v1", false, 4);
    }

    public static BacktestConfig defaultsNextBarOnly(TradingStyle style) {
        return new BacktestConfig(style, 50, 2.0, 0.0, "v1", false, 1);
    }

    public BacktestConfig {
        if (lookbackBars < 1) {
            throw new IllegalArgumentException("lookbackBars must be >= 1");
        }
        if (spreadPts < 0 || slippagePts < 0) {
            throw new IllegalArgumentException("spreadPts and slippagePts must be >= 0");
        }
        if (fillValidityBars < 1) {
            throw new IllegalArgumentException("fillValidityBars must be >= 1");
        }
    }
}
