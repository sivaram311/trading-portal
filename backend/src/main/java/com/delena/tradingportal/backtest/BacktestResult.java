package com.delena.tradingportal.backtest;

import com.delena.tradingportal.engine.style.TradingStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Aggregated backtest metrics and per-trade summaries (DEEP-ALGORITHMS §9.4 subset). */
public record BacktestResult(
        TradingStyle style,
        int barsProcessed,
        int tradeCount,
        double winRate,
        double profitFactor,
        double expectancyR,
        double maxDrawdownPct,
        double avgWinR,
        double avgLossR,
        double totalR,
        List<TradeSummary> trades
) {
    /** Sample artifact path written by {@link BacktesterTest} and {@link #writeSampleCsv(Path)}. */
    public static final String SAMPLE_METRICS_RELATIVE = "target/backtest-sample-metrics.csv";

    public record TradeSummary(
            Instant entryTime,
            Instant exitTime,
            String direction,
            String mode,
            String grade,
            double entryPrice,
            double exitPrice,
            double rMultiple,
            String exitReason
    ) {}

    public String toCsv() {
        String header = "style,bars_processed,trade_count,win_rate,profit_factor,expectancy_r,"
                + "max_drawdown_pct,avg_win_r,avg_loss_r,total_r";
        String summary = String.format(Locale.US,
                "%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                style, barsProcessed, tradeCount, winRate, profitFactor, expectancyR,
                maxDrawdownPct, avgWinR, avgLossR, totalR);

        String tradeHeader = "entry_time,exit_time,direction,mode,grade,entry_price,exit_price,r_multiple,exit_reason";
        String tradeRows = trades.stream()
                .map(t -> String.format(Locale.US, "%s,%s,%s,%s,%s,%.2f,%.2f,%.4f,%s",
                        t.entryTime(), t.exitTime(), t.direction(), t.mode(), t.grade(),
                        t.entryPrice(), t.exitPrice(), t.rMultiple(), t.exitReason()))
                .collect(Collectors.joining("\n"));

        return header + "\n" + summary + "\n\n" + tradeHeader + "\n" + tradeRows + "\n";
    }

    /** Writes {@link #toCsv()} to {@code path} (creates parent dirs). */
    public void writeSampleCsv(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toCsv());
    }

    static BacktestResult empty(TradingStyle style) {
        return new BacktestResult(style, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List.of());
    }

    /** R-multiple sequence in trade order (for Monte-Carlo shuffle). */
    public List<Double> rMultiples() {
        return trades.stream().map(TradeSummary::rMultiple).toList();
    }

    /** Cumulative R equity curve from closed trades. */
    public List<Double> equityCurve() {
        return cumulativeEquity(trades);
    }

    /** Recompute headline metrics from an arbitrary trade subset (e.g. walk-forward test window). */
    public static BacktestResult fromTrades(TradingStyle style, int barsProcessed, List<TradeSummary> trades) {
        return fromTrades(style, barsProcessed, trades, null);
    }

    static BacktestResult fromTrades(TradingStyle style, int barsProcessed,
                                     List<TradeSummary> trades, List<Double> equitySteps) {
        int n = trades.size();
        if (n == 0) {
            // Preserve barsProcessed so operators can see the walk ran (fail-closed / no fills).
            return new BacktestResult(style, barsProcessed, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List.of());
        }

        double totalR = trades.stream().mapToDouble(TradeSummary::rMultiple).sum();
        long wins = trades.stream().filter(t -> t.rMultiple() > 0).count();
        double winRate = (double) wins / n;

        double grossProfit = trades.stream().filter(t -> t.rMultiple() > 0)
                .mapToDouble(TradeSummary::rMultiple).sum();
        double grossLoss = Math.abs(trades.stream().filter(t -> t.rMultiple() < 0)
                .mapToDouble(TradeSummary::rMultiple).sum());
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 999.99 : 0.0);

        double expectancyR = totalR / n;

        List<Double> curve = equitySteps == null || equitySteps.isEmpty()
                ? cumulativeEquity(trades) : equitySteps;
        double maxDdPct = Backtester.maxDrawdownPct(curve);

        double avgWin = trades.stream().filter(t -> t.rMultiple() > 0)
                .mapToDouble(TradeSummary::rMultiple).average().orElse(0.0);
        double avgLoss = trades.stream().filter(t -> t.rMultiple() < 0)
                .mapToDouble(TradeSummary::rMultiple).average().orElse(0.0);

        return new BacktestResult(style, barsProcessed, n, winRate, profitFactor, expectancyR,
                maxDdPct, avgWin, avgLoss, totalR, List.copyOf(trades));
    }

    private static List<Double> cumulativeEquity(List<TradeSummary> trades) {
        List<Double> curve = new java.util.ArrayList<>();
        double eq = 0.0;
        for (TradeSummary t : trades) {
            eq += t.rMultiple();
            curve.add(eq);
        }
        return curve;
    }
}
