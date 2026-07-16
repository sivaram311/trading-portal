package com.delena.tradingportal.paper;

import com.delena.tradingportal.engine.gann.GannConfig;
import com.delena.tradingportal.engine.ict.IctConfig;
import com.delena.tradingportal.engine.style.StyleProfile;
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
    private final Instant t0 = Instant.parse("2026-07-15T12:00:00Z");

    @BeforeEach
    void setUp() {
        manager = new PositionManager();
        dayStyle = new StyleProfile(
                IctConfig.defaults(), GannConfig.defaults(),
                0.625, 1, Duration.ofHours(8),
                false, 1.0, 0.45, 32.0);
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
                stop, remaining, beActive, t1Hit);
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
