package com.delena.tradingportal.engine.risk;

import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.RiskVerdict;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Risk Gate (docs/algorithms/AUTOMATION-PIPELINE.md §3 Stage D). Evaluated after every
 * {@link ConfluenceDecision}, even when automation=allow. Produces a {@link RiskVerdict}.
 *
 * <p>Hard rules for the MVP paper slice:
 * <ul>
 *   <li>automation=deny (CONFLICT / grade F) is never ok.</li>
 *   <li>max 1 open paper position on XAUUSD.</li>
 *   <li>daily loss halt at -2R (stub: caller supplies running daily R).</li>
 *   <li>grade floor: paper allows A/A+ (live path is absent).</li>
 * </ul>
 */
@Component
public class RiskGate {

    private static final double PAPER_EQUITY = 100_000.0;
    private static final double MAX_RISK_PCT = 0.5;
    private static final double DAILY_LOSS_HALT_R = -2.0;
    private static final int MAX_OPEN_POSITIONS = 1;

    public RiskVerdict verdict(ConfluenceDecision d, int openPositions, double dailyLossR) {
        List<String> deny = new ArrayList<>();

        boolean confluenceAllows = !"deny".equals(d.automation());
        if (!confluenceAllows) {
            deny.add("RISK_CONFLUENCE_DENY");
        }

        boolean dataGap = d.reasons().stream().anyMatch(r -> r.endsWith("DATA_GAP"));
        if (dataGap) {
            deny.add("RISK_DATA_GAP");
        }

        boolean newsVeto = d.reasons().contains("NEWS_VETO");

        // Sizing: risk <= 0.5% equity at the decision's stop distance.
        double entryMid = (d.entry().low() + d.entry().high()) / 2.0;
        double stopDistance = Math.abs(entryMid - d.stop());
        Double size = null;
        Double riskPct = null;
        boolean maxRiskPass = true;
        if (stopDistance > 0 && !"flat".equals(d.direction())) {
            double riskCash = PAPER_EQUITY * (MAX_RISK_PCT / 100.0);
            size = round(riskCash / stopDistance);
            riskPct = MAX_RISK_PCT;
        } else if (!"NONE".equals(d.mode())) {
            // Actionable mode but no usable stop distance -> cannot size safely.
            maxRiskPass = false;
        }
        if (!maxRiskPass) {
            deny.add("RISK_MAX_RISK_PER_TRADE");
        }

        boolean dailyLossPass = dailyLossR > DAILY_LOSS_HALT_R;
        if (!dailyLossPass) {
            deny.add("RISK_MAX_DAILY_LOSS");
        }

        boolean openPass = openPositions < MAX_OPEN_POSITIONS;
        if (!openPass) {
            deny.add("RISK_MAX_OPEN_POSITIONS");
        }

        boolean newsPass = !newsVeto;
        if (!newsPass) {
            deny.add("RISK_NEWS_VETO");
        }

        // Midday new-entry suppression surfaces as a spread/session deny.
        boolean midday = d.reasons().contains("MIDDAY_CAP");
        boolean spreadSessionPass = !midday;
        if (!spreadSessionPass) {
            deny.add("RISK_MIDDAY_SESSION");
        }

        boolean duplicatePass = true;

        // Grade floor: paper allows A / A+.
        boolean gradeFloorPass = "A".equals(d.grade()) || "A+".equals(d.grade());
        if (!gradeFloorPass) {
            deny.add("RISK_GRADE_FLOOR");
        }

        boolean ok = confluenceAllows && !dataGap && maxRiskPass && dailyLossPass && openPass
                && newsPass && spreadSessionPass && gradeFloorPass;

        if (!ok) {
            size = null;
            riskPct = null;
        }

        var checks = new RiskVerdict.Checks(maxRiskPass, dailyLossPass, openPass, newsPass,
                spreadSessionPass, duplicatePass, gradeFloorPass);
        return new RiskVerdict(d.id(), Instant.now(), ok, size, riskPct, dailyLossR,
                openPositions, deny, checks);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
