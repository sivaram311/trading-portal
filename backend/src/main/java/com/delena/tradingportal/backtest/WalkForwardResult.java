package com.delena.tradingportal.backtest;

import java.util.List;

/** Out-of-sample walk-forward folds (test windows only) plus pooled OOS metrics. */
public record WalkForwardResult(
        List<BacktestResult> folds,
        double aggregateExpectancyR,
        double aggregateProfitFactor,
        double aggregateMaxDrawdownPct
) {
    static WalkForwardResult empty() {
        return new WalkForwardResult(List.of(), 0.0, 0.0, 0.0);
    }
}
