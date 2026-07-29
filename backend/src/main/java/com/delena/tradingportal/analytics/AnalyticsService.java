package com.delena.tradingportal.analytics;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.model.PaperJournalEntry;
import com.delena.tradingportal.persistence.PaperJournalEntity;
import com.delena.tradingportal.persistence.PaperJournalRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Paper-journal analytics (expectancy / win-rate / session breakdown) computed from closed
 * {@code paper_journal} rows. Read-only, paper-only — no broker/live calls
 * (docs/FEATURE-VALIDATION-0.3.1.md "Analytics dashboard: PARTIAL_CSV_API" gap this closes for
 * the JSON API; CSV export via {@code POST /api/backtest/run} is unaffected).
 *
 * <p>Response field names match the live frontend contract
 * ({@code frontend/src/app/core/models.ts} AnalyticsSummary / AnalyticsBySessionResponse).
 *
 * <p>Fails soft: unparsable/legacy payload rows are skipped rather than surfaced as errors, and
 * an empty journal yields zeroed/null stats instead of a 4xx/5xx.
 */
@Service
public class AnalyticsService {

    private static final String CLOSED_STATUS = "PAPER_CLOSED";

    private final PaperJournalRepository journalRepo;
    private final Json json;

    public AnalyticsService(PaperJournalRepository journalRepo, Json json) {
        this.journalRepo = journalRepo;
        this.json = json;
    }

    /** Headline expectancy/win-rate + optional mode/killzone group hints (only groups with data). */
    public AnalyticsSummary summary() {
        List<ClosedTrade> trades = closedTrades();
        TradeStats overall = TradeStats.of(rMultiples(trades));
        Map<String, TradeStats> byMode = groupStats(trades, ClosedTrade::mode);
        Map<String, TradeStats> byKillzone = groupStats(trades, ClosedTrade::killzone);
        return new AnalyticsSummary(overall.tradeCount(), overall.winRate(), overall.expectancyR(),
                overall.profitFactor(), overall.avgWinR(), overall.avgLossR(), overall.totalR(),
                Instant.now(), byMode, byKillzone);
    }

    /** Breakdown by ICT killzone (NyTime.killzone) when any closed trade has one, else by NY hour bucket. */
    public AnalyticsBySessionResponse bySession() {
        List<ClosedTrade> trades = closedTrades();
        boolean anyKillzone = trades.stream().anyMatch(t -> t.killzone() != null);
        Map<String, TradeStats> buckets = anyKillzone
                ? groupStats(trades, t -> t.killzone() != null ? t.killzone() : "OUTSIDE_KILLZONE")
                : groupStats(trades, ClosedTrade::hourBucket);
        List<SessionRow> rows = new ArrayList<>();
        buckets.forEach((label, stats) -> rows.add(new SessionRow(label, stats.tradeCount(),
                stats.winRate(), stats.expectancyR())));
        return new AnalyticsBySessionResponse(rows);
    }

    // ------------------------------------------------------------------ helpers

    private List<ClosedTrade> closedTrades() {
        List<PaperJournalEntity> rows = journalRepo.findByStatusIn(List.of(CLOSED_STATUS));
        List<ClosedTrade> out = new ArrayList<>();
        for (PaperJournalEntity row : rows) {
            ClosedTrade trade = toClosedTrade(row);
            if (trade != null) {
                out.add(trade);
            }
        }
        return out;
    }

    private ClosedTrade toClosedTrade(PaperJournalEntity row) {
        try {
            PaperJournalEntry entry = json.read(row.getPayload(), PaperJournalEntry.class);
            if (entry.paper() == null || entry.paper().rMultiple() == null) {
                return null;
            }
            Instant at = entry.paper().closedAt() != null ? entry.paper().closedAt() : entry.detectedAt();
            String killzone = at != null ? NyTime.killzone(at) : null;
            Integer hour = at != null ? NyTime.toNy(at).getHour() : null;
            return new ClosedTrade(entry.mode(), entry.grade(), entry.direction(),
                    entry.paper().rMultiple(), killzone, hour);
        } catch (RuntimeException e) {
            // Legacy/unparsable payload — skip this row rather than fail the whole dashboard.
            return null;
        }
    }

    private static List<Double> rMultiples(List<ClosedTrade> trades) {
        return trades.stream().map(ClosedTrade::rMultiple).toList();
    }

    private static Map<String, TradeStats> groupStats(List<ClosedTrade> trades,
                                                       Function<ClosedTrade, String> keyFn) {
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (ClosedTrade t : trades) {
            String key = keyFn.apply(t);
            if (key == null) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t.rMultiple());
        }
        Map<String, TradeStats> out = new LinkedHashMap<>();
        grouped.forEach((key, rs) -> out.put(key, TradeStats.of(rs)));
        return out;
    }

    private record ClosedTrade(String mode, String grade, String direction, double rMultiple,
                               String killzone, Integer hour) {
        String hourBucket() {
            if (hour == null) {
                return "UNKNOWN";
            }
            return String.format("%02d:00-%02d:00", hour, (hour + 1) % 24);
        }
    }

    /** Same convention as {@code BacktestResult}: wins = r&gt;0, losses = r&lt;0, avg_loss_r stays negative. */
    public record TradeStats(long tradeCount, double winRate, double expectancyR, Double profitFactor,
                             Double avgWinR, Double avgLossR, Double totalR) {

        private static final TradeStats EMPTY = new TradeStats(0, 0.0, 0.0, null, null, null, null);

        static TradeStats of(List<Double> rMultiples) {
            int n = rMultiples.size();
            if (n == 0) {
                return EMPTY;
            }
            List<Double> wins = rMultiples.stream().filter(r -> r > 0).toList();
            List<Double> losses = rMultiples.stream().filter(r -> r < 0).toList();
            double totalR = rMultiples.stream().mapToDouble(Double::doubleValue).sum();
            double grossProfit = wins.stream().mapToDouble(Double::doubleValue).sum();
            double grossLoss = Math.abs(losses.stream().mapToDouble(Double::doubleValue).sum());
            double winRate = (double) wins.size() / n;
            double expectancyR = totalR / n;
            double avgWinR = wins.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgLossR = losses.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 999.99 : 0.0);
            return new TradeStats(n, round(winRate), round(expectancyR), round(profitFactor),
                    round(avgWinR), round(avgLossR), round(totalR));
        }

        private static double round(double v) {
            return Math.round(v * 10000.0) / 10000.0;
        }
    }

    /** GET /api/analytics/summary — mirrors frontend/src/app/core/models.ts AnalyticsSummary. */
    public record AnalyticsSummary(long tradeCount, double winRate, double expectancyR, Double profitFactor,
                                   Double avgWinR, Double avgLossR, Double totalR, Instant asOf,
                                   Map<String, TradeStats> byMode, Map<String, TradeStats> byKillzone) {
    }

    public record SessionRow(String session, long tradeCount, double winRate, double expectancyR) {
    }

    /** GET /api/analytics/by-session — mirrors frontend/src/app/core/models.ts AnalyticsBySessionResponse. */
    public record AnalyticsBySessionResponse(List<SessionRow> sessions) {
    }
}
