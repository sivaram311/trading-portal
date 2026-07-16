package com.delena.tradingportal.backtest;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.engine.confluence.ConfluenceEngine;
import com.delena.tradingportal.engine.gann.GannEngine;
import com.delena.tradingportal.engine.ict.IctEngine;
import com.delena.tradingportal.engine.risk.MarketQualityGate;
import com.delena.tradingportal.engine.risk.RiskGate;
import com.delena.tradingportal.engine.style.StyleRegistry;
import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.paper.PositionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkForwardMonteCarloTest {

    private static final int MC_ITERATIONS = 50;

    private Backtester backtester;

    @BeforeEach
    void setUp() {
        backtester = new Backtester(
                new IctEngine(),
                new GannEngine(),
                new ConfluenceEngine(),
                new RiskGate(),
                new MarketQualityGate(),
                new StyleRegistry(),
                new PositionManager());
    }

    @Test
    void walkForwardProducesOosFoldsAndAggregateMetrics() {
        BacktestHistory history = seedLikeHistory();
        int lookback = 40;
        BacktestConfig config = new BacktestConfig(TradingStyle.DAY, lookback, 1.5, 0.0, "v1", false, 24);
        WalkForwardConfig wf = new WalkForwardConfig(30, 15, 15);

        WalkForwardResult wfResult = backtester.walkForward(history, config, wf);

        assertNotNull(wfResult);
        assertFalse(wfResult.folds().isEmpty(), "expected at least one walk-forward fold");
        assertTrue(wfResult.folds().size() >= 2, "rolling step should yield multiple folds");

        for (BacktestResult fold : wfResult.folds()) {
            assertFalse(Double.isNaN(fold.expectancyR()));
            assertFalse(Double.isNaN(fold.profitFactor()));
            assertFalse(Double.isNaN(fold.maxDrawdownPct()));
        }

        assertFalse(Double.isNaN(wfResult.aggregateExpectancyR()));
        assertFalse(Double.isNaN(wfResult.aggregateProfitFactor()));
        assertFalse(Double.isNaN(wfResult.aggregateMaxDrawdownPct()));

        int totalOosTrades = wfResult.folds().stream().mapToInt(BacktestResult::tradeCount).sum();
        assertTrue(totalOosTrades >= 0);
    }

    @Test
    void walkForwardReturnsEmptyWhenHistoryTooShort() {
        BacktestHistory tiny = BacktestHistory.of(List.of(), List.of(bar("M15", Instant.EPOCH, 1, 2, 0.5, 1, 1)), List.of());
        WalkForwardResult wfResult = backtester.walkForward(
                tiny, BacktestConfig.defaults(TradingStyle.DAY), WalkForwardConfig.defaults());

        assertTrue(wfResult.folds().isEmpty());
        assertEquals(0.0, wfResult.aggregateExpectancyR());
    }

    @Test
    void monteCarloSummarizesShuffledTradeDistribution() {
        List<BacktestResult.TradeSummary> trades = List.of(
                trade(Instant.parse("2026-01-01T10:00:00Z"), 1.0),
                trade(Instant.parse("2026-01-01T11:00:00Z"), -0.5),
                trade(Instant.parse("2026-01-01T12:00:00Z"), 2.0),
                trade(Instant.parse("2026-01-01T13:00:00Z"), -1.0),
                trade(Instant.parse("2026-01-01T14:00:00Z"), 0.5),
                trade(Instant.parse("2026-01-01T15:00:00Z"), 1.5));

        BacktestResult base = BacktestResult.fromTrades(TradingStyle.DAY, 100, trades);
        MonteCarloResult mc = backtester.monteCarlo(base, MC_ITERATIONS, 42L);

        assertEquals(MC_ITERATIONS, mc.iterations());
        assertFalse(Double.isNaN(mc.p50ExpectancyR()));
        assertFalse(Double.isNaN(mc.p50MaxDrawdownPct()));
        assertTrue(mc.p5ExpectancyR() <= mc.p50ExpectancyR());
        assertTrue(mc.p50ExpectancyR() <= mc.p95ExpectancyR());
        assertTrue(mc.p5MaxDrawdownPct() <= mc.p50MaxDrawdownPct());
        assertTrue(mc.p50MaxDrawdownPct() <= mc.p95MaxDrawdownPct());
        assertTrue(mc.fractionPositiveExpectancy() >= 0.0 && mc.fractionPositiveExpectancy() <= 1.0);

        // Base expectancy is positive; most shuffles should remain positive.
        assertTrue(mc.fractionPositiveExpectancy() > 0.5);
    }

    @Test
    void monteCarloIsDeterministicForSameSeed() {
        BacktestResult base = BacktestResult.fromTrades(TradingStyle.DAY, 10, List.of(
                trade(Instant.parse("2026-01-01T10:00:00Z"), 1.0),
                trade(Instant.parse("2026-01-01T11:00:00Z"), -0.5),
                trade(Instant.parse("2026-01-01T12:00:00Z"), 0.5)));

        MonteCarloResult a = backtester.monteCarlo(base, MC_ITERATIONS, 99L);
        MonteCarloResult b = backtester.monteCarlo(base, MC_ITERATIONS, 99L);

        assertEquals(a.p50ExpectancyR(), b.p50ExpectancyR(), 1e-9);
        assertEquals(a.p50MaxDrawdownPct(), b.p50MaxDrawdownPct(), 1e-9);
        assertEquals(a.fractionPositiveExpectancy(), b.fractionPositiveExpectancy(), 1e-9);
    }

    @Test
    void backtestResultHelpersExposeRMultiplesAndEquityCurve() {
        List<BacktestResult.TradeSummary> trades = List.of(
                trade(Instant.parse("2026-01-01T10:00:00Z"), 1.0),
                trade(Instant.parse("2026-01-01T11:00:00Z"), 2.0));
        BacktestResult result = BacktestResult.fromTrades(TradingStyle.DAY, 5, trades);

        assertEquals(List.of(1.0, 2.0), result.rMultiples());
        assertEquals(List.of(1.0, 3.0), result.equityCurve());
        assertEquals(1.5, result.expectancyR(), 1e-9);
    }

    // ---- synthetic history (compact seed-like path) ----------------------------

    private static BacktestHistory seedLikeHistory() {
        List<M5> path = buildM5Path();
        List<OhlcBar> m5 = new ArrayList<>();
        for (M5 b : path) {
            m5.add(bar("M5", b.time(), b.open(), b.high(), b.low(), b.close(), b.volume()));
        }
        return BacktestHistory.of(m5, aggregate(path, "M15", 15), aggregate(path, "H1", 60));
    }

    private static BacktestResult.TradeSummary trade(Instant entry, double rMultiple) {
        return new BacktestResult.TradeSummary(
                entry, entry.plusSeconds(3600), "long", "R", "A",
                2000.0, 2010.0, rMultiple, "T1");
    }

    private record M5(Instant time, double open, double high, double low, double close, double volume) {
    }

    private static List<M5> buildM5Path() {
        List<M5> out = new ArrayList<>();
        LocalDate finalDay = recentWeekday(NyTime.sessionDate(Instant.parse("2026-07-15T12:00:00Z")));

        double price = 1980.0;
        for (int d = 3; d >= 1; d--) {
            LocalDate day = previousWeekday(finalDay, d);
            for (LocalTime t = LocalTime.of(0, 0); t.isBefore(LocalTime.of(17, 0)); t = t.plusMinutes(5)) {
                double open = price;
                double drift = 0.03;
                double wobble = ((t.getMinute() / 5) % 2 == 0) ? 0.12 : -0.10;
                double close = open + drift + wobble;
                double high = Math.max(open, close) + 0.15;
                double low = Math.min(open, close) - 0.15;
                out.add(new M5(nyInstant(day, t), r(open), r(high), r(low), r(close), 100));
                price = close;
            }
        }

        double base = price;
        for (LocalTime t = LocalTime.of(0, 0); t.isBefore(LocalTime.of(7, 0)); t = t.plusMinutes(5)) {
            double open = price;
            double target = base;
            if (t.isAfter(LocalTime.of(3, 30)) && t.isBefore(LocalTime.of(4, 30))) {
                target = base - 3.0;
            }
            double close = open + (target - open) * 0.25;
            double high = Math.max(open, close) + 0.10;
            double low = Math.min(open, close) - 0.10;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), 90));
            price = close;
        }

        double nyOpen = price;
        double[] dipCloses = {nyOpen - 1.5, nyOpen - 3.2, nyOpen - 4.0, nyOpen - 3.6};
        LocalTime t = LocalTime.of(7, 0);
        double prev = nyOpen;
        double sweepLow = nyOpen - 5.2;
        for (int i = 0; i < dipCloses.length; i++) {
            double open = prev;
            double close = dipCloses[i];
            double low = (i == 2) ? sweepLow : Math.min(open, close) - 0.4;
            double high = Math.max(open, close) + 0.4;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), 140 + i * 10));
            prev = close;
            t = t.plusMinutes(5);
        }

        double[] upCloses = {prev + 2.0, prev + 4.5, prev + 7.5, prev + 9.5, prev + 11.0};
        double[] vols = {220, 260, 320, 300, 280};
        for (int i = 0; i < upCloses.length; i++) {
            double open = prev;
            double close = upCloses[i];
            double high = Math.max(open, close) + 0.5;
            double low = Math.min(open, close) - 0.3;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), vols[i]));
            prev = close;
            t = t.plusMinutes(5);
        }

        for (int i = 0; i < 24; i++) {
            double open = prev;
            double close = open + 0.8;
            double high = Math.max(open, close) + 0.6;
            double low = Math.min(open, close) - 0.4;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), 150));
            prev = close;
            t = t.plusMinutes(5);
        }
        return out;
    }

    private static List<OhlcBar> aggregate(List<M5> m5, String tf, int minutes) {
        Map<Instant, List<M5>> buckets = new LinkedHashMap<>();
        for (M5 b : m5) {
            Instant bucket = floorTo(b.time(), minutes);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(b);
        }
        List<OhlcBar> out = new ArrayList<>();
        for (var e : buckets.entrySet()) {
            List<M5> bs = e.getValue();
            double open = bs.get(0).open();
            double close = bs.get(bs.size() - 1).close();
            double high = bs.stream().mapToDouble(M5::high).max().orElse(open);
            double low = bs.stream().mapToDouble(M5::low).min().orElse(open);
            double vol = bs.stream().mapToDouble(M5::volume).sum();
            out.add(bar(tf, e.getKey(), open, high, low, close, vol));
        }
        return out;
    }

    private static OhlcBar bar(String tf, Instant ts, double o, double h, double l, double c, double v) {
        return new OhlcBar("XAUUSD", tf, ts, ts, o, h, l, c, v, ts);
    }

    private static Instant floorTo(Instant ts, int minutes) {
        var ny = NyTime.toNy(ts);
        int totalMin = ny.getHour() * 60 + ny.getMinute();
        int floored = (totalMin / minutes) * minutes;
        LocalDateTime base = ny.toLocalDate().atStartOfDay().plusMinutes(floored);
        return base.atZone(NyTime.NY_ZONE).toInstant();
    }

    private static Instant nyInstant(LocalDate day, LocalTime time) {
        return LocalDateTime.of(day, time).atZone(NyTime.NY_ZONE).toInstant();
    }

    private static LocalDate recentWeekday(LocalDate d) {
        LocalDate x = d;
        while (x.getDayOfWeek() == DayOfWeek.SATURDAY || x.getDayOfWeek() == DayOfWeek.SUNDAY) {
            x = x.minusDays(1);
        }
        return x;
    }

    private static LocalDate previousWeekday(LocalDate from, int businessDaysBack) {
        LocalDate x = from;
        int moved = 0;
        while (moved < businessDaysBack) {
            x = x.minusDays(1);
            if (x.getDayOfWeek() != DayOfWeek.SATURDAY && x.getDayOfWeek() != DayOfWeek.SUNDAY) {
                moved++;
            }
        }
        return x;
    }

    private static double r(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
