package com.delena.tradingportal.backtest;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.engine.confluence.ConfluenceEngine;
import com.delena.tradingportal.engine.gann.GannEngine;
import com.delena.tradingportal.engine.ict.IctEngine;
import com.delena.tradingportal.engine.risk.MarketQualityGate;
import com.delena.tradingportal.engine.risk.RiskGate;
import com.delena.tradingportal.engine.style.StyleProfile;
import com.delena.tradingportal.engine.style.StyleRegistry;
import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.model.PaperJournalEntry;
import com.delena.tradingportal.model.RiskVerdict;
import com.delena.tradingportal.paper.PaperDecisionPolicy;
import com.delena.tradingportal.paper.PositionManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Bar-by-bar paper backtest using real ICT/Gann/Confluence/Risk/MarketQuality engines and
 * {@link PositionManager} lifecycle rules. No live broker; single-leg only.
 */
@Component
public class Backtester {

    private final IctEngine ictEngine;
    private final GannEngine gannEngine;
    private final ConfluenceEngine confluenceEngine;
    private final RiskGate riskGate;
    private final MarketQualityGate marketQualityGate;
    private final StyleRegistry styleRegistry;
    private final PositionManager positionManager;

    public Backtester(IctEngine ictEngine, GannEngine gannEngine, ConfluenceEngine confluenceEngine,
                      RiskGate riskGate, MarketQualityGate marketQualityGate, StyleRegistry styleRegistry,
                      PositionManager positionManager) {
        this.ictEngine = ictEngine;
        this.gannEngine = gannEngine;
        this.confluenceEngine = confluenceEngine;
        this.riskGate = riskGate;
        this.marketQualityGate = marketQualityGate;
        this.styleRegistry = styleRegistry;
        this.positionManager = positionManager;
    }

    /** Run with default config for the given style. */
    public BacktestResult run(BacktestHistory history, TradingStyle style) {
        return run(history, BacktestConfig.defaults(style));
    }

    public BacktestResult run(BacktestHistory history, BacktestConfig config) {
        if (history == null || history.m15() == null || history.m15().size() < config.lookbackBars() + 2) {
            return BacktestResult.empty(config.style());
        }

        StyleProfile profile = styleRegistry.get(config.style());
        List<OhlcBar> m15 = history.m15();
        int start = config.lookbackBars();
        int end = m15.size() - 1;

        List<BacktestResult.TradeSummary> closedTrades = new ArrayList<>();
        List<Double> equityCurve = new ArrayList<>();

        PaperJournalEntry openPosition = null;
        PendingOrder pending = null;
        Instant lastSameDirTs = null;
        double sessionDailyR = 0.0;
        LocalDate sessionDay = null;
        int barsProcessed = 0;

        for (int i = start; i < end; i++) {
            OhlcBar bar = m15.get(i);
            OhlcBar nextBar = m15.get(i + 1);
            Instant asof = bar.ts();
            barsProcessed++;

            LocalDate barSession = NyTime.sessionDate(asof);
            if (sessionDay == null || !sessionDay.equals(barSession)) {
                sessionDay = barSession;
                sessionDailyR = 0.0;
            }

            List<OhlcBar> m5Win = upTo(history.m5(), asof);
            List<OhlcBar> m15Win = upTo(history.m15(), asof);
            List<OhlcBar> h1Win = upTo(history.h1(), asof);
            List<OhlcBar> h4Win = upTo(history.h4(), asof);
            List<OhlcBar> d1Win = upTo(history.d1(), asof);

            IctSnapshot ict = ictEngine.compute(h4Win, h1Win, m15Win, m5Win, asof, profile.ict());
            var gann = gannEngine.compute(m5Win.isEmpty() ? m15Win : m5Win, d1Win, asof, profile.gann(), "NY_OPEN");
            ConfluenceDecision decision = confluenceEngine.decide(ict, gann, asof, config.newsVeto(),
                    config.weightsVersion());

            int openCount = openPosition != null ? 1 : 0;
            RiskVerdict risk = MarketQualityGate.mergeIntoVerdict(
                    riskGate.verdict(decision, openCount, sessionDailyR),
                    marketQualityGate.evaluate(marketQualityGate.contextFromBars(
                            m5Win.isEmpty() ? m15Win : m5Win, asof, decision.direction(),
                            lastSameDirTs, java.util.Optional.of(profile.maxSpreadPts()), config.style())));

            boolean structureFlip = structureFlip(openPosition, ict);

            if (openPosition != null) {
                double atr = PositionManager.atr(m15Win);
                PositionManager.BarResult mgmt = positionManager.onBar(openPosition, nextBar, profile, atr,
                        structureFlip);
                openPosition = mgmt.entry();
                if (mgmt.closed()) {
                    closedTrades.add(toSummary(openPosition));
                    double r = openPosition.paper().rMultiple() != null ? openPosition.paper().rMultiple() : 0.0;
                    sessionDailyR += r;
                    appendEquity(equityCurve, r);
                    openPosition = null;
                }
            }

            if (pending != null && openPosition == null) {
                FillSimulator.FillResult fill = FillSimulator.simulate(
                        pending.decision().entry(), pending.decision().direction(), nextBar,
                        config.spreadPts(), config.slippagePts(), config.style(), profile);
                if (fill.filled()) {
                    openPosition = openPaperEntry(pending.decision(), fill.fillPrice(), nextBar.ts(), risk);
                    pending = null;
                } else {
                    int remaining = pending.barsRemaining() - 1;
                    pending = remaining > 0 ? pending.withBarsRemaining(remaining) : null;
                }
            }

            if (openPosition == null && pending == null
                    && PaperDecisionPolicy.isConfirmable(decision, risk)) {
                pending = new PendingOrder(decision, config.fillValidityBars());
                lastSameDirTs = asof;
            }
        }

        return buildResult(config.style(), barsProcessed, closedTrades, equityCurve);
    }

    /**
     * Rolling walk-forward on M15: each fold runs {@link #run} on history {@code [0, foldEnd)} so engines
     * see train+test context, but metrics are scored only on trades entered in the OOS test segment.
     */
    public WalkForwardResult walkForward(BacktestHistory history, BacktestConfig config, WalkForwardConfig wf) {
        if (history == null || history.m15() == null) {
            return WalkForwardResult.empty();
        }
        List<OhlcBar> m15 = history.m15();
        int lookback = config.lookbackBars();
        int minBars = lookback + wf.trainBars() + wf.testBars();
        if (m15.size() < minBars) {
            return WalkForwardResult.empty();
        }

        List<BacktestResult> folds = new ArrayList<>();
        List<BacktestResult.TradeSummary> allTestTrades = new ArrayList<>();

        for (int offset = 0; ; offset += wf.stepBars()) {
            int testStartIdx = lookback + offset + wf.trainBars();
            int testEndIdx = testStartIdx + wf.testBars();
            int foldEnd = testEndIdx;
            if (foldEnd > m15.size()) {
                break;
            }

            BacktestHistory slice = history.prefixThroughM15Index(foldEnd);
            BacktestResult full = run(slice, config);

            Instant testStart = m15.get(testStartIdx).ts();
            Instant testEndExclusive = m15.get(testEndIdx).ts();

            List<BacktestResult.TradeSummary> testTrades = full.trades().stream()
                    .filter(t -> !t.entryTime().isBefore(testStart) && t.entryTime().isBefore(testEndExclusive))
                    .toList();

            folds.add(BacktestResult.fromTrades(config.style(), full.barsProcessed(), testTrades));
            allTestTrades.addAll(testTrades);
        }

        if (folds.isEmpty()) {
            return WalkForwardResult.empty();
        }

        BacktestResult aggregate = BacktestResult.fromTrades(config.style(), 0, allTestTrades);
        return new WalkForwardResult(folds, aggregate.expectancyR(), aggregate.profitFactor(),
                aggregate.maxDrawdownPct());
    }

    /**
     * Monte-Carlo robustness: permute trade R-multiples, rebuild equity curves, summarize distribution.
     */
    public MonteCarloResult monteCarlo(BacktestResult base, int iterations, long seed) {
        if (base == null || iterations <= 0) {
            return MonteCarloResult.empty();
        }
        List<Double> rMults = base.rMultiples();
        if (rMults.isEmpty()) {
            return MonteCarloResult.empty();
        }

        Random rng = new Random(seed);
        List<Double> expectancies = new ArrayList<>(iterations);
        List<Double> maxDds = new ArrayList<>(iterations);
        int positive = 0;

        for (int i = 0; i < iterations; i++) {
            List<Double> shuffled = new ArrayList<>(rMults);
            Collections.shuffle(shuffled, rng);

            double expectancy = shuffled.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            expectancies.add(expectancy);
            if (expectancy > 0.0) {
                positive++;
            }

            List<Double> curve = cumulativeFromRMultiples(shuffled);
            maxDds.add(maxDrawdownPct(curve));
        }

        Collections.sort(expectancies);
        Collections.sort(maxDds);

        return new MonteCarloResult(
                iterations,
                percentile(expectancies, 5),
                percentile(expectancies, 50),
                percentile(expectancies, 95),
                percentile(maxDds, 5),
                percentile(maxDds, 50),
                percentile(maxDds, 95),
                (double) positive / iterations);
    }

    private static List<Double> cumulativeFromRMultiples(List<Double> rMults) {
        List<Double> curve = new ArrayList<>();
        double eq = 0.0;
        for (double r : rMults) {
            eq += r;
            curve.add(eq);
        }
        return curve;
    }

    static double percentile(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) {
            return 0.0;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double w = rank - lo;
        return sorted.get(lo) * (1.0 - w) + sorted.get(hi) * w;
    }

    private record PendingOrder(ConfluenceDecision decision, int barsRemaining) {
        PendingOrder withBarsRemaining(int bars) {
            return new PendingOrder(decision, bars);
        }
    }

    private static PaperJournalEntry openPaperEntry(ConfluenceDecision d, double fillPrice, Instant when,
                                                    RiskVerdict risk) {
        var paper = new PaperJournalEntry.Paper(when, null, round(fillPrice), null, null, null,
                null, null, d.stop(), 1.0, false, false);
        return new PaperJournalEntry(
                UUID.randomUUID().toString(), d.id(), d.symbol(),
                NyTime.sessionDate(when), "PAPER_OPEN", d.mode(), d.direction(), d.grade(), d.score(),
                d.reasons(), d.weightsVersion(), d.entry(), d.stop(), d.targets(), d.invalidIf(),
                d.automation(),
                new PaperJournalEntry.RiskSummary(risk.ok(), risk.size(), risk.denyReasons()),
                d.ts(), when, "backtester", null, paper);
    }

    private static BacktestResult.TradeSummary toSummary(PaperJournalEntry entry) {
        var p = entry.paper();
        return new BacktestResult.TradeSummary(
                p.openedAt(), p.closedAt(), entry.direction(), entry.mode(), entry.grade(),
                p.entryPrice(), p.exitPrice(),
                p.rMultiple() != null ? p.rMultiple() : 0.0,
                p.exitReason() != null ? p.exitReason() : "UNKNOWN");
    }

    private static BacktestResult buildResult(TradingStyle style, int barsProcessed,
                                              List<BacktestResult.TradeSummary> trades,
                                              List<Double> equitySteps) {
        return BacktestResult.fromTrades(style, barsProcessed, trades, equitySteps);
    }

    private static void appendEquity(List<Double> curve, double deltaR) {
        double prev = curve.isEmpty() ? 0.0 : curve.get(curve.size() - 1);
        curve.add(prev + deltaR);
    }

    static double maxDrawdownPct(List<Double> equityCurve) {
        if (equityCurve.isEmpty()) {
            return 0.0;
        }
        double peak = equityCurve.get(0);
        double maxDd = 0.0;
        for (double eq : equityCurve) {
            peak = Math.max(peak, eq);
            double dd = peak - eq;
            maxDd = Math.max(maxDd, dd);
        }
        if (peak <= 0) {
            return maxDd * 100.0;
        }
        return (maxDd / peak) * 100.0;
    }

    static boolean structureFlip(PaperJournalEntry open, IctSnapshot ict) {
        if (open == null || ict == null || ict.structure() == null) {
            return false;
        }
        String event = ict.structure().event();
        if (event == null || "NONE".equals(event)) {
            return false;
        }
        String structDir = ict.structure().direction();
        if (structDir == null) {
            return false;
        }
        boolean posLong = "long".equalsIgnoreCase(open.direction());
        boolean structLong = "long".equalsIgnoreCase(structDir);
        return posLong != structLong && ("MSS".equals(event) || "BOS".equals(event));
    }

    static List<OhlcBar> upTo(List<OhlcBar> bars, Instant asof) {
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        List<OhlcBar> out = new ArrayList<>();
        for (OhlcBar b : bars) {
            if (!b.ts().isAfter(asof)) {
                out.add(b);
            }
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
