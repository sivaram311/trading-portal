package com.delena.tradingportal.paper;

import com.delena.tradingportal.engine.style.StyleProfile;

/**
 * Pure ADD_LEG (limited pyramiding) gate for the paper path
 * (docs/algorithms/DEEP-ALGORITHMS-AND-CALCULATIONS.md §7).
 *
 * <p>An ADD_LEG never opens a new position — it adds size to the ONE already-open paper
 * position; {@code RiskGate.MAX_OPEN_POSITIONS} stays 1 (see {@link PositionManager#tryAddLeg}
 * / {@code PaperTradingService}). It is allowed only when:
 * <ul>
 *   <li>the new signal agrees with the open position's direction;</li>
 *   <li>the new signal is grade A or A+;</li>
 *   <li>the open position's unrealized R is &gt;= {@link #MIN_UNREALIZED_R};</li>
 *   <li>the style's total per-trade risk cap ({@link StyleProfile#riskPct()}) is not exceeded
 *       after the add; and</li>
 *   <li>the open position has fewer legs than the style allows ({@link StyleProfile#maxLegs()}:
 *       SCALP=1, DAY=2, POSITIONAL=3).</li>
 * </ul>
 *
 * <p>Kept side-effect free so it is directly unit-testable, mirroring {@link PaperDecisionPolicy}.
 */
public final class PyramidPolicy {

    /** Returned by {@link #rejectionReason} when every gate passes. */
    public static final String OK = "OK";

    /** Minimum unrealized R the open position must show before a new leg can be added. */
    public static final double MIN_UNREALIZED_R = 0.8;

    /**
     * Fraction of the original leg's risk/size that one ADD_LEG contributes. Spec allows 50-70%
     * of original; fixed at the midpoint so paper sizing stays deterministic.
     */
    public static final double ADD_LEG_SIZE_FRACTION = 0.6;

    /**
     * Paper's fixed per-leg risk (% of equity), mirroring {@code RiskGate.MAX_RISK_PCT}. RiskGate
     * does not yet size legs per style (see {@link StyleProfile} javadoc), so every leg — initial
     * or added — is assumed to risk this fraction of equity, scaled by
     * {@link #ADD_LEG_SIZE_FRACTION} for adds. Duplicated here (rather than importing RiskGate)
     * to keep this policy dependency-free and unit-testable in isolation.
     */
    public static final double BASE_LEG_RISK_PCT = 0.5;

    private PyramidPolicy() {
    }

    /**
     * @param openDirection   direction of the already-open paper position ("long"/"short")
     * @param signalDirection direction of the new confluence signal being considered for an add
     * @param signalGrade     grade of the new confluence signal ("A"/"A+"/...)
     * @param unrealizedR     current unrealized R multiple of the open position
     * @param currentLegs     number of legs already filled on the open position (&gt;= 1)
     * @param style           style profile in effect (maxLegs / riskPct)
     */
    public static boolean canAddLeg(String openDirection, String signalDirection, String signalGrade,
                                     double unrealizedR, int currentLegs, StyleProfile style) {
        return OK.equals(rejectionReason(openDirection, signalDirection, signalGrade,
                unrealizedR, currentLegs, style));
    }

    /**
     * Same gate as {@link #canAddLeg} but returns a machine-readable reason on rejection (or
     * {@link #OK}), useful for journal notes / operator-facing diagnostics.
     */
    public static String rejectionReason(String openDirection, String signalDirection, String signalGrade,
                                         double unrealizedR, int currentLegs, StyleProfile style) {
        if (style == null) {
            return "NO_STYLE";
        }
        if (currentLegs < 1) {
            return "INVALID_LEG_COUNT";
        }
        if (openDirection == null || signalDirection == null || !openDirection.equalsIgnoreCase(signalDirection)) {
            return "DIRECTION_MISMATCH";
        }
        if (!isEligibleGrade(signalGrade)) {
            return "GRADE_BELOW_A";
        }
        if (unrealizedR < MIN_UNREALIZED_R) {
            return "UNREALIZED_R_BELOW_THRESHOLD";
        }
        // Prefer MAX_LEGS when the style forbids any further add (e.g. SCALP=1); otherwise the
        // risk-cap check is the binding paper constraint (DAY maxLegs=2 but riskPct=0.625).
        if (currentLegs >= style.maxLegs()) {
            return "MAX_LEGS_REACHED";
        }
        if (totalRiskPctAfterAdd(currentLegs) > style.riskPct()) {
            return "RISK_CAP_EXCEEDED";
        }
        return OK;
    }

    /** Total per-trade risk (% of equity) if one more leg is added on top of {@code currentLegs}. */
    public static double totalRiskPctAfterAdd(int currentLegs) {
        return BASE_LEG_RISK_PCT * (1 + currentLegs * ADD_LEG_SIZE_FRACTION);
    }

    /** Risk (% of equity) contributed by a single added leg. */
    public static double addLegRiskPct() {
        return BASE_LEG_RISK_PCT * ADD_LEG_SIZE_FRACTION;
    }

    private static boolean isEligibleGrade(String grade) {
        return "A".equals(grade) || "A+".equals(grade);
    }
}
