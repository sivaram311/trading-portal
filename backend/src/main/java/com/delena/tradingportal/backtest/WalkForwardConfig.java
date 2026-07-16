package com.delena.tradingportal.backtest;

/** Rolling walk-forward window sizes on M15 bars (DEEP-ALGORITHMS §9.4). */
public record WalkForwardConfig(int trainBars, int testBars, int stepBars) {

    public static final int DEFAULT_TRAIN = 200;
    public static final int DEFAULT_TEST = 60;
    public static final int DEFAULT_STEP = 60;

    public WalkForwardConfig {
        if (trainBars < 1 || testBars < 1 || stepBars < 1) {
            throw new IllegalArgumentException("trainBars, testBars, and stepBars must be >= 1");
        }
    }

    public static WalkForwardConfig defaults() {
        return new WalkForwardConfig(DEFAULT_TRAIN, DEFAULT_TEST, DEFAULT_STEP);
    }
}
