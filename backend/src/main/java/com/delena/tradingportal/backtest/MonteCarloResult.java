package com.delena.tradingportal.backtest;

/** Distribution of expectancy and max drawdown from shuffled trade R-multiples (§9.4). */
public record MonteCarloResult(
        int iterations,
        double p5ExpectancyR,
        double p50ExpectancyR,
        double p95ExpectancyR,
        double p5MaxDrawdownPct,
        double p50MaxDrawdownPct,
        double p95MaxDrawdownPct,
        double fractionPositiveExpectancy
) {
    static MonteCarloResult empty() {
        return new MonteCarloResult(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
