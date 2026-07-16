package com.delena.tradingportal.engine.risk;

import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.model.RiskVerdict;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketQualityGateTest {

    private static final Instant NOW = Instant.parse("2026-07-15T14:00:00Z");

    private final MarketQualityGate gate = new MarketQualityGate(
            Duration.ofMinutes(30), 2.0, 1.5, 14, 50);

    @Test
    void spreadAboveThresholdDenies() {
        var ctx = baseContext().spreadPts(50).maxSpreadPts(40).build();
        assertTrue(gate.evaluate(ctx).contains(MarketQualityGate.QUALITY_SPREAD));
    }

    @Test
    void atrRegimeExtremeDenies() {
        var ctx = baseContext().atrCurrent(5).atrLong(2).build();
        assertTrue(gate.evaluate(ctx).contains(MarketQualityGate.QUALITY_ATR_EXTREME));
    }

    @Test
    void largeGapDenies() {
        var ctx = baseContext().atrCurrent(4).gapPts(7.0).build();
        assertTrue(gate.evaluate(ctx).contains(MarketQualityGate.QUALITY_GAP));
    }

    @Test
    void duplicateSameDirectionWithinWindowDenies() {
        var ctx = baseContext()
                .direction("long")
                .lastSameDirDecisionTs(NOW.minus(Duration.ofMinutes(10)))
                .build();
        assertTrue(gate.evaluate(ctx).contains(MarketQualityGate.QUALITY_DUPLICATE));
    }

    @Test
    void duplicateOutsideWindowPasses() {
        var ctx = baseContext()
                .direction("long")
                .lastSameDirDecisionTs(NOW.minus(Duration.ofMinutes(45)))
                .build();
        assertFalse(gate.evaluate(ctx).contains(MarketQualityGate.QUALITY_DUPLICATE));
    }

    @Test
    void missingAtrDeniesData() {
        var ctx = baseContext().atrLong(0).build();
        List<String> denies = gate.evaluate(ctx);
        assertEquals(List.of(MarketQualityGate.QUALITY_DATA), denies);
    }

    @Test
    void cleanContextPasses() {
        assertTrue(gate.evaluate(baseContext().build()).isEmpty());
    }

    @Test
    void resolveMaxSpreadUsesStyleProfileWhenPresent() {
        assertEquals(32.0, gate.resolveMaxSpreadPts(Optional.of(32.0), TradingStyle.DAY));
    }

    @Test
    void resolveMaxSpreadFallsBackToStyleDefaults() {
        assertEquals(25.0, gate.resolveMaxSpreadPts(Optional.empty(), TradingStyle.SCALP));
        assertEquals(40.0, gate.resolveMaxSpreadPts(Optional.empty(), TradingStyle.DAY));
        assertEquals(50.0, gate.resolveMaxSpreadPts(Optional.empty(), TradingStyle.POSITIONAL));
    }

    @Test
    void mergeIntoVerdictAppendsDeniesAndForcesNotOk() {
        RiskVerdict risk = new RiskVerdict("d1", NOW, true, 1.0, 0.5, 0.0, 0,
                List.of(), new RiskVerdict.Checks(true, true, true, true, true, true, true));
        RiskVerdict merged = MarketQualityGate.mergeIntoVerdict(risk,
                List.of(MarketQualityGate.QUALITY_SPREAD));
        assertFalse(merged.ok());
        assertTrue(merged.denyReasons().contains(MarketQualityGate.QUALITY_SPREAD));
        assertEquals(null, merged.size());
    }

    private static ContextBuilder baseContext() {
        return new ContextBuilder()
                .decisionTs(NOW)
                .spreadPts(10)
                .atrCurrent(2)
                .atrLong(2)
                .gapPts(1.0)
                .direction("short")
                .maxSpreadPts(40);
    }

    private static final class ContextBuilder {
        private Instant decisionTs = NOW;
        private double spreadPts = 10;
        private double atrCurrent = 2;
        private double atrLong = 2;
        private Double gapPts = 1.0;
        private Instant lastSameDirDecisionTs;
        private String direction = "short";
        private double maxSpreadPts = 40;

        ContextBuilder decisionTs(Instant v) {
            this.decisionTs = v;
            return this;
        }

        ContextBuilder spreadPts(double v) {
            this.spreadPts = v;
            return this;
        }

        ContextBuilder atrCurrent(double v) {
            this.atrCurrent = v;
            return this;
        }

        ContextBuilder atrLong(double v) {
            this.atrLong = v;
            return this;
        }

        ContextBuilder gapPts(Double v) {
            this.gapPts = v;
            return this;
        }

        ContextBuilder lastSameDirDecisionTs(Instant v) {
            this.lastSameDirDecisionTs = v;
            return this;
        }

        ContextBuilder direction(String v) {
            this.direction = v;
            return this;
        }

        ContextBuilder maxSpreadPts(double v) {
            this.maxSpreadPts = v;
            return this;
        }

        MarketQualityGate.QualityContext build() {
            return new MarketQualityGate.QualityContext(decisionTs, spreadPts, atrCurrent, atrLong,
                    gapPts, lastSameDirDecisionTs, direction, maxSpreadPts);
        }
    }
}
