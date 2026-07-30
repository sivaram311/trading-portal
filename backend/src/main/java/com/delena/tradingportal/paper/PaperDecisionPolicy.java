package com.delena.tradingportal.paper;

import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.RiskVerdict;

/**
 * Pure confirmability guard enforcing the Q1 gate: "CONFLICT / risk deny never creates paper
 * entries". A decision is confirmable into a PAPER_OPEN position only when the confluence layer
 * did not deny it, it is an actionable mode ({@code R} or {@code C} — not {@code NONE} or
 * watch-only {@code T}), it is not grade F, and the Risk Gate verdict is ok.
 * Mode T is alert/watch per CONFLUENCE-FRAMEWORK § Mode T ("not auto-entry alone").
 * Kept side-effect free so it is directly unit-testable.
 */
public final class PaperDecisionPolicy {

    private PaperDecisionPolicy() {
    }

    public static boolean isConfirmable(ConfluenceDecision decision, RiskVerdict risk) {
        if (decision == null || risk == null) {
            return false;
        }
        return isConfirmable(decision.automation(), decision.grade(), decision.mode(), risk.ok());
    }

    public static boolean isConfirmable(String automation, String grade, String mode, boolean riskOk) {
        if ("deny".equals(automation)) {
            return false;
        }
        if (mode == null || "NONE".equals(mode) || "T".equals(mode)) {
            return false;
        }
        if ("F".equals(grade)) {
            return false;
        }
        return riskOk;
    }
}
