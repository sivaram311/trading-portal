package com.delena.tradingportal.paper;

import com.delena.tradingportal.engine.gann.GannConfig;
import com.delena.tradingportal.engine.ict.IctConfig;
import com.delena.tradingportal.engine.style.StyleProfile;
import com.delena.tradingportal.engine.style.StyleRegistry;
import com.delena.tradingportal.engine.style.TradingStyle;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.Entry;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.model.PaperJournalEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionManagerTest {

    private PositionManager manager;
    private StyleProfile dayStyle;
    private StyleProfile positionalStyle;
    private final Instant t0 = Instant.parse("2026-07-15T12:00:00Z");

    @BeforeEach
    void setUp() {
        manager = new PositionManager();
        // Match StyleRegistry DAY: riskPct=0.625, maxLegs=2 (ADD_LEG gated by risk cap on paper).
        dayStyle = new StyleProfile(
                IctConfig.defaults(), GannConfig.defaults(),
                0.625, 2, Duration.ofHours(8),
                false, 1.0, 0.45, 32.0);
        positionalStyle = new StyleProfile(
                IctConfig.defaults(), GannConfig.defaults(),
                0.875, 3, Duration.ofDays(5),
                false, 1.0, 0.40, 40.0);
    }

    @Test
    void movesStopToBreakEvenAfterPlusOneR() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        OhlcBar bar = bar(t0.plusSeconds(900), 2005, 2010, 2004, 2008);

        PositionManager.BarResult result = manager.onBar(open, bar, dayStyle, 5.0, false);

        assertFalse(result.closed());
        assertEquals("PAPER_OPEN", result.status());
        assertTrue(result.entry().paper().beActive());
        assertEquals(2000.5, result.entry().paper().currentStop());
        assertEquals(1.0, result.entry().paper().mfeR());
    }

    @Test
    void scalesOutAtT1AndMarksPartial() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        open = openWithPaper(open, paper(open, 2000.0, 2000.5, 1.0, true, false));

        OhlcBar bar = bar(t0.plusSeconds(1800), 2015, 2021, 2014, 2020);

        PositionManager.BarResult result = manager.onBar(open, bar, dayStyle, 5.0, false);

        assertFalse(result.closed());
        assertEquals("PARTIAL", result.status());
        assertTrue(result.entry().paper().t1Hit());
        assertEquals(0.55, result.entry().paper().remainingSize());
        assertEquals(2000.5, result.entry().paper().currentStop());
    }

    @Test
    void stopHitClosesWithNegativeRMultiple() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));

        OhlcBar bar = bar(t0.plusSeconds(900), 1995, 1996, 1989, 1990);

        PositionManager.BarResult result = manager.onBar(open, bar, dayStyle, 5.0, false);

        assertTrue(result.closed());
        assertEquals("PAPER_CLOSED", result.status());
        assertEquals("STOP", result.entry().paper().exitReason());
        assertEquals(-1.0, result.entry().paper().rMultiple());
        assertEquals(1990.0, result.entry().paper().exitPrice());
    }

    @Test
    void beStopClosesWithSmallPositiveR() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        open = openWithPaper(open, paper(open, 2000.0, 2000.5, 1.0, true, false));

        OhlcBar bar = bar(t0.plusSeconds(1800), 2002, 2003, 2000.4, 2001);

        PositionManager.BarResult result = manager.onBar(open, bar, dayStyle, 5.0, false);

        assertTrue(result.closed());
        assertEquals("BE_STOP", result.entry().paper().exitReason());
        assertEquals(0.05, result.entry().paper().rMultiple());
    }

    @Test
    void structureFlipForceClosesAtBarClose() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        OhlcBar bar = bar(t0.plusSeconds(900), 2003, 2005, 2002, 2004);

        PositionManager.BarResult result = manager.onBar(open, bar, dayStyle, 5.0, true);

        assertTrue(result.closed());
        assertEquals("STRUCTURE_FLIP", result.entry().paper().exitReason());
        assertEquals(2004.0, result.entry().paper().exitPrice());
        assertEquals(0.4, result.entry().paper().rMultiple());
    }

    @Test
    void tryAddLegAddsWhenPolicyAllows() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        OhlcBar bar = bar(t0.plusSeconds(900), 2005, 2010, 2004, 2008);
        ConfluenceDecision signal = confluenceSignal("XAUUSD", "long", "A+");

        PositionManager.AddLegResult result = manager.tryAddLeg(open, signal, bar, positionalStyle);

        assertTrue(result.added());
        assertEquals("OK", result.reason());
        assertEquals(2, result.entry().paper().legCount());
        assertEquals(1, result.entry().paper().addLegs().size());
        assertEquals(2008.0, result.entry().paper().addLegs().get(0).entryPrice());
        assertEquals(1990.0, result.entry().paper().addLegs().get(0).stopAtAdd());
        assertEquals(0.6, result.entry().paper().addLegs().get(0).sizeFraction());
        // Status/entryPrice/stop of the managed position are untouched by the add.
        assertEquals("PAPER_OPEN", result.entry().status());
        assertEquals(2000.0, result.entry().paper().entryPrice());
    }

    @Test
    void tryAddLegRejectsBelowUnrealizedRThreshold() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        OhlcBar bar = bar(t0.plusSeconds(900), 2003, 2004, 2002, 2003);
        ConfluenceDecision signal = confluenceSignal("XAUUSD", "long", "A+");

        PositionManager.AddLegResult result = manager.tryAddLeg(open, signal, bar, positionalStyle);

        assertFalse(result.added());
        assertEquals("UNREALIZED_R_BELOW_THRESHOLD", result.reason());
        assertEquals(open, result.entry());
    }

    @Test
    void tryAddLegRejectsOnDayStyleRiskCap() {
        // Real DAY profile (maxLegs=2) so the rejection exercises the risk cap, not maxLegs.
        StyleProfile realDayStyle = new StyleRegistry().get(TradingStyle.DAY);
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        OhlcBar bar = bar(t0.plusSeconds(900), 2005, 2010, 2004, 2008);
        ConfluenceDecision signal = confluenceSignal("XAUUSD", "long", "A+");

        PositionManager.AddLegResult result = manager.tryAddLeg(open, signal, bar, realDayStyle);

        assertFalse(result.added());
        assertEquals("RISK_CAP_EXCEEDED", result.reason());
    }

    @Test
    void tryAddLegRejectsWhenNotOpen() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        PaperJournalEntry closed = new PaperJournalEntry(open.id(), open.decisionId(), open.symbol(),
                open.sessionDate(), "PAPER_CLOSED", open.mode(), open.direction(), open.grade(), open.score(),
                open.reasons(), open.weightsVersion(), open.entry(), open.stop(), open.targets(),
                open.invalidIf(), open.automation(), open.risk(), open.detectedAt(), open.actionedAt(),
                open.actionedBy(), open.actionNote(), open.paper());
        OhlcBar bar = bar(t0.plusSeconds(900), 2005, 2010, 2004, 2008);
        ConfluenceDecision signal = confluenceSignal("XAUUSD", "long", "A+");

        PositionManager.AddLegResult result = manager.tryAddLeg(closed, signal, bar, positionalStyle);

        assertFalse(result.added());
        assertEquals("NOT_OPEN", result.reason());
    }

    @Test
    void closeBlendsAddedLegRIntoRealizedRMultiple() {
        PaperJournalEntry open = longOpen(2000.0, 1990.0, List.of(2020.0));
        OhlcBar addBar = bar(t0.plusSeconds(900), 2005, 2010, 2004, 2008);
        ConfluenceDecision signal = confluenceSignal("XAUUSD", "long", "A+");
        PositionManager.AddLegResult added = manager.tryAddLeg(open, signal, addBar, positionalStyle);
        assertTrue(added.added());

        PositionManager.BarResult closed = manager.closeAt(added.entry(), t0.plusSeconds(1800), 2020.0, "MANUAL");

        assertTrue(closed.closed());
        // Original leg: full size, R = (2020-2000)/10 = 2.0.
        // Added leg: 0.6 size, R = (2020-2008)/(2008-1990) = 12/18 = 0.6667 -> weighted 0.4.
        assertEquals(2.4, closed.entry().paper().rMultiple(), 0.001);
        assertEquals(1, closed.entry().paper().addLegs().size());
    }

    private static ConfluenceDecision confluenceSignal(String symbol, String direction, String grade) {
        return new ConfluenceDecision("dec-" + UUID.randomUUID(), symbol, Instant.parse("2026-07-15T12:15:00Z"),
                "R", direction, grade, 8.0, "agree", List.of(), new Entry("OB", 2003.0, 2007.0), 1995.0,
                List.of(2030.0), List.of(), new ConfluenceDecision.Engines("ict-ref", "gann-ref"),
                "confirm", "v1");
    }

    private PaperJournalEntry longOpen(double entry, double stop, List<Double> targets) {
        var p = paper(null, entry, stop, 1.0, false, false);
        return new PaperJournalEntry(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "XAUUSD", LocalDate.of(2026, 7, 15), "PAPER_OPEN",
                "R", "long", "A", 7.0, List.of(), "v1",
                new Entry("OB", entry - 1, entry + 1), stop, targets,
                List.of("close_below:" + stop), "confirm",
                new PaperJournalEntry.RiskSummary(true, 1.0, List.of()),
                t0, t0, "operator", null, p);
    }

    private static PaperJournalEntry.Paper paper(PaperJournalEntry open, double entry, double stop,
                                                   double remaining, boolean beActive, boolean t1Hit) {
        Instant opened = open != null && open.paper() != null ? open.paper().openedAt() : Instant.parse("2026-07-15T12:00:00Z");
        return new PaperJournalEntry.Paper(opened, null, entry, null, null, null, null, null,
                stop, remaining, beActive, t1Hit, 1, List.of());
    }

    private static PaperJournalEntry openWithPaper(PaperJournalEntry entry, PaperJournalEntry.Paper paper) {
        return new PaperJournalEntry(entry.id(), entry.decisionId(), entry.symbol(), entry.sessionDate(),
                entry.status(), entry.mode(), entry.direction(), entry.grade(), entry.score(),
                entry.reasons(), entry.weightsVersion(), entry.entry(), entry.stop(), entry.targets(),
                entry.invalidIf(), entry.automation(), entry.risk(), entry.detectedAt(),
                entry.actionedAt(), entry.actionedBy(), entry.actionNote(), paper);
    }

    private static OhlcBar bar(Instant ts, double open, double high, double low, double close) {
        return new OhlcBar("XAUUSD", "M15", ts, ts, open, high, low, close, 100, ts);
    }
}
