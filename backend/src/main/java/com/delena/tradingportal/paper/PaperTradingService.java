package com.delena.tradingportal.paper;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.PaperJournalEntry;
import com.delena.tradingportal.model.RiskVerdict;
import com.delena.tradingportal.paper.PaperDecisionPolicy;
import com.delena.tradingportal.persistence.ConfluenceDecisionEntity;
import com.delena.tradingportal.persistence.ConfluenceDecisionRepository;
import com.delena.tradingportal.persistence.PaperJournalEntity;
import com.delena.tradingportal.persistence.PaperJournalRepository;
import com.delena.tradingportal.persistence.RiskVerdictEntity;
import com.delena.tradingportal.persistence.RiskVerdictRepository;
import com.delena.tradingportal.web.ApiExceptions.ConflictException;
import com.delena.tradingportal.web.ApiExceptions.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operator paper-trading actions. Confirm opens a PAPER_OPEN position only when
 * {@link PaperDecisionPolicy} allows it (never on CONFLICT / grade F / risk deny); such attempts
 * are rejected (409) and never create a filled paper position. Paper-only; no live execution.
 */
@Service
public class PaperTradingService {

    private final ConfluenceDecisionRepository decisionRepo;
    private final RiskVerdictRepository riskRepo;
    private final PaperJournalRepository journalRepo;
    private final Json json;

    public PaperTradingService(ConfluenceDecisionRepository decisionRepo, RiskVerdictRepository riskRepo,
                               PaperJournalRepository journalRepo, Json json) {
        this.decisionRepo = decisionRepo;
        this.riskRepo = riskRepo;
        this.journalRepo = journalRepo;
        this.json = json;
    }

    @Transactional
    public JsonNode confirm(String decisionId, String note, String actor) {
        UUID id = parse(decisionId);
        ConfluenceDecisionEntity decision = decisionRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("DECISION_NOT_FOUND", "decision_id not found: " + decisionId));
        RiskVerdictEntity risk = riskRepo.findTopByDecisionIdOrderByTsDesc(id).orElse(null);
        boolean riskOk = risk != null && risk.isOk();

        if (!PaperDecisionPolicy.isConfirmable(decision.getAutomation(), decision.getGrade(), decision.getMode(), riskOk)) {
            throw new ConflictException("NOT_CONFIRMABLE",
                    "Decision is not confirmable (automation/grade/mode/risk): grade=" + decision.getGrade()
                            + " mode=" + decision.getMode() + " automation=" + decision.getAutomation() + " riskOk=" + riskOk);
        }
        if (journalRepo.countByStatus("PAPER_OPEN") >= 1) {
            throw new ConflictException("MAX_OPEN_POSITIONS", "A paper position is already open (max 1 on XAUUSD).");
        }

        PaperJournalEntity row = journalRepo.findTopByDecisionIdOrderByCreatedAtDesc(id)
                .orElseThrow(() -> new NotFoundException("JOURNAL_NOT_FOUND", "No journal row for decision " + decisionId));
        openPaperPosition(row, actor, note);
        return json.read(row.getPayload());
    }

    /**
     * Auto-open paper for an A+ ALERTED decision when the pipeline flag is ON. Returns true when
     * a PAPER_OPEN row was written. Never throws — pipeline must not fail on auto-confirm errors.
     */
    public boolean autoOpenIfEligible(ConfluenceDecision decision, RiskVerdict risk, String actor) {
        if (decision == null || risk == null || !"A+".equals(decision.grade())) {
            return false;
        }
        if (!PaperDecisionPolicy.isConfirmable(decision, risk)) {
            return false;
        }
        if (journalRepo.countByStatus("PAPER_OPEN") >= 1) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(decision.id());
        } catch (IllegalArgumentException e) {
            return false;
        }
        PaperJournalEntity row = journalRepo.findTopByDecisionIdOrderByCreatedAtDesc(id).orElse(null);
        if (row == null || !"ALERTED".equals(row.getStatus())) {
            return false;
        }
        try {
            openPaperPosition(row, actor, "A+ auto-confirm");
            return true;
        } catch (ConflictException e) {
            return false;
        }
    }

    @Transactional
    public JsonNode dismiss(String decisionId, String reason, String actor) {
        UUID id = parse(decisionId);
        decisionRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("DECISION_NOT_FOUND", "decision_id not found: " + decisionId));
        PaperJournalEntity row = journalRepo.findTopByDecisionIdOrderByCreatedAtDesc(id)
                .orElseThrow(() -> new NotFoundException("JOURNAL_NOT_FOUND", "No journal row for decision " + decisionId));
        ensureActionable(row);

        PaperJournalEntry current = json.read(row.getPayload(), PaperJournalEntry.class);
        Instant now = Instant.now();
        PaperJournalEntry updated = withAction(current, "DISMISSED", now, actor, reason, current.paper());
        applyUpdate(row, "DISMISSED", now, actor, reason, updated);
        return json.read(row.getPayload());
    }

    public Dtos_JournalPage journal(String grade, String mode, String direction, String status,
                                    LocalDate sessionDate, Instant from, Instant to, int limit, int offset) {
        Specification<PaperJournalEntity> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (grade != null) {
                ps.add(cb.equal(root.get("grade"), grade));
            }
            if (mode != null) {
                ps.add(cb.equal(root.get("mode"), mode));
            }
            if (direction != null) {
                ps.add(cb.equal(root.get("direction"), direction));
            }
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (sessionDate != null) {
                ps.add(cb.equal(root.get("sessionDate"), sessionDate));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("detectedAt"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        int pageNo = offset / Math.max(1, limit);
        Page<PaperJournalEntity> page = journalRepo.findAll(spec,
                PageRequest.of(pageNo, limit, Sort.by(Sort.Direction.DESC, "detectedAt")));
        List<JsonNode> items = page.getContent().stream().map(r -> json.read(r.getPayload())).toList();
        return new Dtos_JournalPage(items, page.getTotalElements());
    }

    public record Dtos_JournalPage(List<JsonNode> items, long total) {
    }

    // ------------------------------------------------------------------ helpers

    private void openPaperPosition(PaperJournalEntity row, String actor, String note) {
        ensureActionable(row);
        PaperJournalEntry current = json.read(row.getPayload(), PaperJournalEntry.class);
        Instant now = Instant.now();
        double entryMid = round((current.entry().low() + current.entry().high()) / 2.0);
        var paper = new PaperJournalEntry.Paper(now, null, entryMid, null, null, null, null, null);
        PaperJournalEntry updated = withAction(current, "PAPER_OPEN", now, actor, note, paper);
        applyUpdate(row, "PAPER_OPEN", now, actor, note, updated);
    }

    private void ensureActionable(PaperJournalEntity row) {
        String s = row.getStatus();
        if ("PAPER_OPEN".equals(s) || "PAPER_CLOSED".equals(s) || "DISMISSED".equals(s)) {
            throw new ConflictException("ALREADY_ACTIONED", "Decision already actioned (status=" + s + ").");
        }
    }

    private void applyUpdate(PaperJournalEntity row, String status, Instant when, String actor,
                             String note, PaperJournalEntry updated) {
        row.setStatus(status);
        row.setActionedAt(when);
        row.setActionedBy(actor);
        row.setActionNote(note);
        row.setPayload(json.write(updated));
        journalRepo.save(row);
    }

    private static PaperJournalEntry withAction(PaperJournalEntry c, String status, Instant when,
                                                String actor, String note, PaperJournalEntry.Paper paper) {
        return new PaperJournalEntry(c.id(), c.decisionId(), c.symbol(), c.sessionDate(), status,
                c.mode(), c.direction(), c.grade(), c.score(), c.reasons(), c.weightsVersion(),
                c.entry(), c.stop(), c.targets(), c.invalidIf(), c.automation(), c.risk(),
                c.detectedAt(), when, actor, note, paper);
    }

    private static UUID parse(String decisionId) {
        try {
            return UUID.fromString(decisionId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("DECISION_NOT_FOUND", "decision_id not found: " + decisionId);
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
