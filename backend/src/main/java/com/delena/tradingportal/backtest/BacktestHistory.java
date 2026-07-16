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

    /**
     * Prefix slice through M15 index {@code endExclusive} (bars {@code [0, endExclusive)}).
     * Higher timeframes are trimmed to the last included M15 timestamp.
     */
    public BacktestHistory prefixThroughM15Index(int endExclusive) {
        if (m15 == null || m15.isEmpty() || endExclusive <= 0) {
            return new BacktestHistory(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        int end = Math.min(endExclusive, m15.size());
        List<OhlcBar> m15Slice = List.copyOf(m15.subList(0, end));
        var cutoff = m15Slice.get(m15Slice.size() - 1).ts();
        return new BacktestHistory(
                Backtester.upTo(m5, cutoff),
                m15Slice,
                Backtester.upTo(h1, cutoff),
                Backtester.upTo(h4, cutoff),
                Backtester.upTo(d1, cutoff));
    }
}
