package com.delena.tradingportal.web;

import com.delena.tradingportal.backtest.BacktestConfig;
import com.delena.tradingportal.backtest.BacktestHistory;
import com.delena.tradingportal.backtest.BacktestResult;
import com.delena.tradingportal.backtest.Backtester;
import com.delena.tradingportal.backtest.MonteCarloResult;
import com.delena.tradingportal.backtest.WalkForwardConfig;
import com.delena.tradingportal.backtest.WalkForwardResult;
import com.delena.tradingportal.config.TradingProperties;
import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.market.MarketDataService;
import com.delena.tradingportal.model.OhlcBar;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paper-only backtest surface (DEEP-ALGORITHMS §9). Uses stored OHLC; no broker.
 */
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private static final int DEFAULT_MAX_M15 = 400;
    private static final int HARD_MAX_M15 = 2000;

    private final Backtester backtester;
    private final MarketDataService market;
    private final TradingProperties props;

    public BacktestController(Backtester backtester, MarketDataService market, TradingProperties props) {
        this.backtester = backtester;
        this.market = market;
        this.props = props;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paper_only", true);
        out.put("bar_by_bar", true);
        out.put("walk_forward", true);
        out.put("monte_carlo", true);
        out.put("styles", List.of("SCALP", "DAY", "POSITIONAL"));
        out.put("active_style", props.getStyle().name());
        out.put("m15_bars_available", market.count("M15"));
        out.put("csv_export", "embedded in POST /api/backtest/run response field trades_csv");
        return out;
    }

    @PostMapping("/run")
    public Map<String, Object> run(
            @RequestParam(value = "maxBars", required = false) Integer maxBars,
            @RequestParam(value = "style", required = false) String styleParam,
            @RequestParam(value = "walkForward", defaultValue = "false") boolean walkForward,
            @RequestParam(value = "monteCarlo", defaultValue = "false") boolean monteCarlo,
            @RequestParam(value = "mcIterations", defaultValue = "200") int mcIterations) {
        TradingStyle style = parseStyle(styleParam);
        int cap = maxBars == null ? DEFAULT_MAX_M15 : Math.min(HARD_MAX_M15, Math.max(80, maxBars));
        BacktestHistory history = loadHistory(cap);
        if (history.m15().size() < 80) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Need >=80 M15 bars in store; have " + history.m15().size());
        }
        BacktestConfig config = BacktestConfig.defaults(style);
        BacktestResult result = backtester.run(history, config);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("style", result.style().name());
        out.put("bars_processed", result.barsProcessed());
        out.put("trade_count", result.tradeCount());
        out.put("win_rate", result.winRate());
        out.put("profit_factor", result.profitFactor());
        out.put("expectancy_r", result.expectancyR());
        out.put("max_drawdown_pct", result.maxDrawdownPct());
        out.put("avg_win_r", result.avgWinR());
        out.put("avg_loss_r", result.avgLossR());
        out.put("total_r", result.totalR());
        out.put("trades_csv", result.toCsv());
        out.put("m15_bars_used", history.m15().size());

        if (walkForward) {
            WalkForwardResult wf = backtester.walkForward(history, config, WalkForwardConfig.defaults());
            out.put("walk_forward", Map.of(
                    "folds", wf.folds().size(),
                    "expectancy_r", wf.aggregateExpectancyR(),
                    "profit_factor", wf.aggregateProfitFactor(),
                    "max_drawdown_pct", wf.aggregateMaxDrawdownPct()));
        }
        if (monteCarlo) {
            MonteCarloResult mc = backtester.monteCarlo(result, Math.min(2000, Math.max(50, mcIterations)), 42L);
            out.put("monte_carlo", Map.of(
                    "iterations", mc.iterations(),
                    "p5_expectancy_r", mc.p5ExpectancyR(),
                    "p50_expectancy_r", mc.p50ExpectancyR(),
                    "p95_expectancy_r", mc.p95ExpectancyR(),
                    "fraction_positive_expectancy", mc.fractionPositiveExpectancy()));
        }
        return out;
    }

    private TradingStyle parseStyle(String styleParam) {
        if (styleParam == null || styleParam.isBlank()) {
            return props.getStyle();
        }
        try {
            return TradingStyle.valueOf(styleParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "style must be SCALP|DAY|POSITIONAL");
        }
    }

    private BacktestHistory loadHistory(int maxM15) {
        List<OhlcBar> m15All = market.bars("M15");
        List<OhlcBar> m15 = m15All.size() <= maxM15
                ? m15All
                : m15All.subList(m15All.size() - maxM15, m15All.size());
        if (m15.isEmpty()) {
            return BacktestHistory.of(List.of(), List.of(), List.of());
        }
        var from = m15.get(0).ts();
        var to = m15.get(m15.size() - 1).ts();
        List<OhlcBar> m5 = market.bars("M5", from, to);
        List<OhlcBar> h1 = market.bars("H1", from, to);
        List<OhlcBar> h4 = market.bars("H4", from, to);
        List<OhlcBar> d1 = market.bars("D1", from, to);
        return BacktestHistory.of(m5, m15, h1, h4, d1);
    }
}
