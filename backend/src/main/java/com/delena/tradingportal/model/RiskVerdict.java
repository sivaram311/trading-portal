package com.delena.tradingportal.model;

import java.time.Instant;
import java.util.List;

/** Output of the Risk Gate (docs/contracts/schemas/risk-verdict.json). */
public record RiskVerdict(
        String decisionId,
        Instant ts,
        boolean ok,
        Double size,
        Double riskPerTradePct,
        double dailyLossR,
        int openPositions,
        List<String> denyReasons,
        Checks checks
) {
    public record Checks(
            boolean maxRiskPerTrade,
            boolean maxDailyLoss,
            boolean maxOpenPositions,
            boolean newsVeto,
            boolean spreadSession,
            boolean duplicateSuppression,
            boolean gradeFloor
    ) {
    }
}
