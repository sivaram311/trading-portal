package com.delena.tradingportal.analytics;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.model.Entry;
import com.delena.tradingportal.model.PaperJournalEntry;
import com.delena.tradingportal.persistence.PaperJournalEntity;
import com.delena.tradingportal.persistence.PaperJournalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private PaperJournalRepository journalRepo;

    private AnalyticsService analytics;
    private Json json;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        json = new Json(mapper);
        analytics = new AnalyticsService(journalRepo, json);
    }

    @Test
    void summaryIsEmptyWhenNoClosedTrades() {
        when(journalRepo.findByStatusIn(List.of("PAPER_CLOSED"))).thenReturn(List.of());

        AnalyticsService.AnalyticsSummary summary = analytics.summary();

        assertEquals(0, summary.tradeCount());
        assertEquals(0.0, summary.winRate());
        assertEquals(0.0, summary.expectancyR());
        assertNull(summary.profitFactor());
        assertNull(summary.totalR());
        assertTrue(summary.byMode().isEmpty());
    }

    @Test
    void summaryComputesExpectancyWinRateAndModeBreakdown() {
        List<PaperJournalEntity> rows = List.of(
                closedRow("R", "A+", "long", 2.0, Instant.parse("2026-07-15T11:15:00Z")), // NY 07:15 -> NY_OPEN
                closedRow("R", "A+", "long", -1.0, Instant.parse("2026-07-15T11:30:00Z")), // NY 07:30 -> NY_OPEN
                closedRow("C", "A", "short", 1.5, Instant.parse("2026-07-15T06:30:00Z"))  // NY 02:30 -> LONDON_OPEN
        );
        when(journalRepo.findByStatusIn(List.of("PAPER_CLOSED"))).thenReturn(rows);

        AnalyticsService.AnalyticsSummary summary = analytics.summary();

        assertEquals(3, summary.tradeCount());
        // AnalyticsService.TradeStats rounds to 4 d.p. for the API surface.
        assertEquals(0.6667, summary.winRate(), 1e-9);
        assertEquals(0.8333, summary.expectancyR(), 1e-9);
        assertEquals(2, summary.byMode().get("R").tradeCount());
        assertEquals(1, summary.byMode().get("C").tradeCount());
        assertEquals(2, summary.byKillzone().get("NY_OPEN").tradeCount());
        assertEquals(1, summary.byKillzone().get("LONDON_OPEN").tradeCount());
    }

    @Test
    void bySessionUsesKillzoneLabelsWhenAvailable() {
        List<PaperJournalEntity> rows = List.of(
                closedRow("R", "A+", "long", 2.0, Instant.parse("2026-07-15T11:15:00Z")),
                closedRow("C", "A", "short", 1.5, Instant.parse("2026-07-15T06:30:00Z")));
        when(journalRepo.findByStatusIn(List.of("PAPER_CLOSED"))).thenReturn(rows);

        AnalyticsService.AnalyticsBySessionResponse breakdown = analytics.bySession();

        List<String> sessions = breakdown.sessions().stream().map(AnalyticsService.SessionRow::session).toList();
        assertTrue(sessions.contains("NY_OPEN"));
        assertTrue(sessions.contains("LONDON_OPEN"));
    }

    @Test
    void bySessionFallsBackToHourBucketWhenNoKillzoneTrades() {
        // 2026-07-15T22:00:00Z -> NY 18:00 (EDT, UTC-4), outside all defined killzones.
        List<PaperJournalEntity> rows = List.of(
                closedRow("T", "B", "long", 0.5, Instant.parse("2026-07-15T22:00:00Z")));
        when(journalRepo.findByStatusIn(List.of("PAPER_CLOSED"))).thenReturn(rows);

        AnalyticsService.AnalyticsBySessionResponse breakdown = analytics.bySession();

        assertEquals(1, breakdown.sessions().size());
        assertEquals("18:00-19:00", breakdown.sessions().get(0).session());
    }

    @Test
    void skipsRowsWithoutClosedPaperData() {
        PaperJournalEntry openEntry = anEntry("R", "A+", "long", null, null);
        PaperJournalEntity openRow = toEntity(openEntry, "PAPER_CLOSED");
        when(journalRepo.findByStatusIn(List.of("PAPER_CLOSED"))).thenReturn(List.of(openRow));

        AnalyticsService.AnalyticsSummary summary = analytics.summary();

        assertEquals(0, summary.tradeCount());
    }

    private PaperJournalEntity closedRow(String mode, String grade, String direction, double rMultiple,
                                         Instant closedAt) {
        PaperJournalEntry entry = anEntry(mode, grade, direction, rMultiple, closedAt);
        return toEntity(entry, "PAPER_CLOSED");
    }

    private PaperJournalEntry anEntry(String mode, String grade, String direction, Double rMultiple,
                                      Instant closedAt) {
        PaperJournalEntry.Paper paper = rMultiple == null ? null
                : new PaperJournalEntry.Paper(closedAt, closedAt, 2000.0, 2000.0 + rMultiple, "MANUAL",
                        rMultiple, rMultiple, -0.2, 2000.0, 1.0, false, false, null, null);
        return new PaperJournalEntry(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "XAUUSD", LocalDate.of(2026, 7, 15), "PAPER_CLOSED", mode, direction, grade, 8.0,
                List.of(), "v1", new Entry("OB", 2006, 2009), 2012.0, List.of(), List.of(),
                "confirm", new PaperJournalEntry.RiskSummary(true, 1.0, List.of()), closedAt,
                closedAt, "system", null, paper);
    }

    private PaperJournalEntity toEntity(PaperJournalEntry entry, String status) {
        return new PaperJournalEntity(UUID.randomUUID(), UUID.fromString(entry.decisionId()), "XAUUSD",
                entry.sessionDate(), status, entry.mode(), entry.direction(), entry.grade(), entry.score(),
                entry.weightsVersion(), entry.automation(), entry.detectedAt(), entry.actionedAt(),
                entry.actionedBy(), entry.actionNote(), json.write(entry));
    }
}
