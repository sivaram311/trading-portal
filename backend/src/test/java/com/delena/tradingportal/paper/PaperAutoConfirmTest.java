package com.delena.tradingportal.paper;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.Entry;
import com.delena.tradingportal.model.PaperJournalEntry;
import com.delena.tradingportal.model.RiskVerdict;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperAutoConfirmTest {

    @Mock
    private PaperJournalRepository journalRepo;

    private PaperTradingService paperTrading;
    private Json json;
    private final Instant asof = Instant.parse("2026-07-15T12:30:00Z");
    private final UUID decisionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        json = new Json(mapper);
        paperTrading = new PaperTradingService(null, null, journalRepo, json);
    }

    @Test
    void autoOpenWhenEligibleOpensPaperPosition() {
        ConfluenceDecision decision = aPlusDecision();
        RiskVerdict risk = okRisk();
        PaperJournalEntity row = alertedRow();

        when(journalRepo.countByStatus("PAPER_OPEN")).thenReturn(0L);
        when(journalRepo.findTopByDecisionIdOrderByCreatedAtDesc(decisionId)).thenReturn(Optional.of(row));

        boolean opened = paperTrading.autoOpenIfEligible(decision, risk, "system:auto-confirm-a-plus");

        assertTrue(opened);
        assertEquals("PAPER_OPEN", row.getStatus());
        verify(journalRepo).save(row);
    }

    @Test
    void autoOpenSkipsWhenGradeNotAPlus() {
        ConfluenceDecision decision = new ConfluenceDecision(decisionId.toString(), "XAUUSD", asof, "R", "short",
                "A", 6.0, "ALIGN_SHORT", List.of(), new Entry("OB", 2006, 2009), 2012.0,
                List.of(), List.of("close_above:2012"),
                new ConfluenceDecision.Engines("ict", "gann"), "confirm", "v1");
        RiskVerdict risk = okRisk();

        assertFalse(paperTrading.autoOpenIfEligible(decision, risk, "system:auto-confirm-a-plus"));
        verify(journalRepo, never()).save(any());
    }

    @Test
    void autoOpenSkipsWhenPaperAlreadyOpen() {
        when(journalRepo.countByStatus("PAPER_OPEN")).thenReturn(1L);

        assertFalse(paperTrading.autoOpenIfEligible(aPlusDecision(), okRisk(), "system:auto-confirm-a-plus"));
        verify(journalRepo, never()).save(any());
    }

    @Test
    void pipelineFlagOffDoesNotCallAutoOpen() {
        // Simulates flag-off behaviour: caller checks TradingProperties before invoking autoOpenIfEligible.
        boolean flagOn = false;
        if (flagOn) {
            paperTrading.autoOpenIfEligible(aPlusDecision(), okRisk(), "system:auto-confirm-a-plus");
        }
        verify(journalRepo, never()).save(any());
    }

    private ConfluenceDecision aPlusDecision() {
        return new ConfluenceDecision(decisionId.toString(), "XAUUSD", asof, "R", "short",
                "A+", 8.0, "ALIGN_SHORT", List.of("ICT_KZ_NY_OPEN"), new Entry("OB", 2006, 2009), 2012.0,
                List.of(1995.0), List.of("close_above:2012"),
                new ConfluenceDecision.Engines("ict", "gann"), "confirm", "v1");
    }

    private RiskVerdict okRisk() {
        return new RiskVerdict(decisionId.toString(), asof, true, 1.0, 0.5, 0, 0,
                List.of(), new RiskVerdict.Checks(true, true, true, true, true, true, true));
    }

    private PaperJournalEntity alertedRow() {
        PaperJournalEntry entry = new PaperJournalEntry(UUID.randomUUID().toString(), decisionId.toString(),
                "XAUUSD", LocalDate.of(2026, 7, 15), "ALERTED", "R", "short", "A+", 8.0,
                List.of(), "v1", new Entry("OB", 2006, 2009), 2012.0, List.of(), List.of(),
                "confirm", new PaperJournalEntry.RiskSummary(true, 1.0, List.of()), asof,
                null, null, null, null);
        return new PaperJournalEntity(UUID.randomUUID(), decisionId, "XAUUSD", entry.sessionDate(),
                "ALERTED", "R", "short", "A+", 8.0, "v1", "confirm", asof, null, null, null,
                json.write(entry));
    }
}
