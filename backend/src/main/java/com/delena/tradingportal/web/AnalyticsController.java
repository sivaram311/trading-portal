package com.delena.tradingportal.web;

import com.delena.tradingportal.analytics.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paper-journal analytics dashboard surface (docs/FEATURE-VALIDATION-0.3.1.md
 * "Analytics dashboard: PARTIAL_CSV_API"). Paper-only, read-only; no broker/live calls.
 * Auth follows the default security chain (CSS JWKS bearer, or dev-bypass header in DEV) — same
 * as every other {@code /api/**} route except {@code /api/health}.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** Expectancy, win rate, avg R, trade count from closed paper trades, with mode/grade/killzone breakdowns. */
    @GetMapping("/summary")
    public AnalyticsService.AnalyticsSummary summary() {
        return analytics.summary();
    }

    /** Closed-trade breakdown by ICT killzone when available, else by NY hour bucket. */
    @GetMapping("/by-session")
    public AnalyticsService.AnalyticsBySessionResponse bySession() {
        return analytics.bySession();
    }
}
