package com.delena.tradingportal.backtest;

import com.delena.tradingportal.model.OhlcBar;

import java.util.List;

/** Multi-timeframe OHLC slices for bar-by-bar replay. H4/D1 optional (empty when absent). */
public record BacktestHistory(
        List<OhlcBar> m5,
        List<OhlcBar> m15,
        List<OhlcBar> h1,
        List<OhlcBar> h4,
        List<OhlcBar> d1
) {
    public static BacktestHistory of(List<OhlcBar> m5, List<OhlcBar> m15, List<OhlcBar> h1) {
        return new BacktestHistory(m5, m15, h1, List.of(), List.of());
    }

    public static BacktestHistory of(List<OhlcBar> m5, List<OhlcBar> m15, List<OhlcBar> h1,
                                     List<OhlcBar> h4, List<OhlcBar> d1) {
        return new BacktestHistory(m5, m15, h1, h4, d1);
    }
}
