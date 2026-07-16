package com.delena.tradingportal.live;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.config.TradingProperties;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * P5 micro-live confirm path. Separate from paper journal statuses ({@code LIVE_*}).
 * Never submits unless {@link LiveExecutionGate} allows.
 */
@Service
public class LiveTradingService {

    private final ConfluenceDecisionRepository decisionRepo;
    private final RiskVerdictRepository riskRepo;
    private final PaperJournalRepository journalRepo;
    private final LiveExecutionGate gate;
    private final SimLiveBroker simBroker;
    private final Mt5BridgeLiveBroker mt5Broker;
    private final TradingProperties props;
    private final Json json;

    public LiveTradingService(ConfluenceDecisionRepository decisionRepo, RiskVerdictRepository riskRepo,
                              PaperJournalRepository journalRepo, LiveExecutionGate gate,
                              SimLiveBroker simBroker, Mt5BridgeLiveBroker mt5Broker,
                              TradingProperties props, Json json) {
        this.decisionRepo = decisionRepo;
        this.riskRepo = riskRepo;
        this.journalRepo = journalRepo;
        this.gate = gate;
        this.simBroker = simBroker;
        this.mt5Broker = mt5Broker;
        this.props = props;
        this.json = json;
    }

    public LiveExecutionGate.GateVerdict gateStatus() {
        return gate.evaluate();
    }

    public boolean isKillSwitchEngaged() {
        return gate.isKillSwitchEngaged();
    }

    public void setKillSwitchEngaged(boolean engaged) {
        gate.setKillSwitchEngaged(engaged);
    }

    @Transactional
    public JsonNode confirm(String decisionId, String note, String actor) {
        LiveExecutionGate.GateVerdict g = gate.evaluate();
        if (!g.ok()) {
            throw new ConflictException("LIVE_GATE", "Live execution denied: " + g.denyReason());
        }

        UUID id = parse(decisionId);
        ConfluenceDecisionEntity decision = decisionRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("DECISION_NOT_FOUND", "decision_id not found: " + decisionId));
        RiskVerdictEntity riskRow = riskRepo.findTopByDecisionIdOrderByTsDesc(id).orElse(null);
        boolean riskOk = riskRow != null && riskRow.isOk();

        if (!PaperDecisionPolicy.isConfirmable(decision.getAutomation(), decision.getGrade(), decision.getMode(), riskOk)) {
            throw new ConflictException("NOT_CONFIRMABLE",
                    "Decision is not confirmable for live: grade=" + decision.getGrade()
                            + " mode=" + decision.getMode() + " automation=" + decision.getAutomation());
        }
        if (!"A+".equals(decision.getGrade()) && !"A".equals(decision.getGrade())) {
            throw new ConflictException("LIVE_GRADE_FLOOR", "Live requires grade A or A+.");
        }
        if (journalRepo.countLiveOpenPositions() >= 1) {
            throw new ConflictException("MAX_LIVE_OPEN", "A live position is already open (max 1).");
        }

        ConfluenceDecision d = json.read(decision.getPayload(), ConfluenceDecision.class);
        RiskVerdict risk = riskRow == null ? null : json.read(riskRow.getPayload(), RiskVerdict.class);
        double requested = risk != null && risk.size() != null ? risk.size() : props.getExec().getMicroMaxLots();
        double lots = Math.min(requested, props.getExec().getMicroMaxLots());
        lots = Math.max(props.getExec().getMicroMinLots(), Math.floor(lots * 100.0) / 100.0);
        if (lots <= 0) {
            throw new ConflictException("LIVE_SIZE", "Micro lot size resolved to zero.");
        }

        double entryRef = (d.entry().low() + d.entry().high()) / 2.0;
        LiveBroker broker = resolveBroker(g.broker());
        LiveBroker.SubmitResult fill = broker.submit(new LiveBroker.LiveOrder(
                d.id(), d.symbol(), d.direction(), lots, entryRef, d.stop(), d.mode(), d.grade()));
        if (!fill.ok()) {
            throw new ConflictException("LIVE_BROKER_REJECT", fill.message());
        }

        Instant now = Instant.now();
        PaperJournalEntry.Paper paper = new PaperJournalEntry.Paper(
                now, null, fill.fillPrice(), null, null, null, null, null,
                d.stop(), 1.0, false, false);
        // Embed live metadata in action note / payload extension via reasons already on entry
        PaperJournalEntry entry = new PaperJournalEntry(
                UUID.randomUUID().toString(), d.id(), d.symbol(), NyTime.sessionDate(d.ts()),
                "LIVE_OPEN", d.mode(), d.direction(), d.grade(), d.score(), d.reasons(),
                d.weightsVersion(), d.entry(), d.stop(), d.targets(), d.invalidIf(), d.automation(),
                new PaperJournalEntry.RiskSummary(true, lots, List.of()),
                d.ts(), now, actor, note == null ? "live-confirm" : note, paper);

        // Persist live-specific fields in JSON via a small overlay map merged into payload
        String payload = mergeLiveMeta(entry, broker.name(), fill.brokerOrderId(), lots);

        journalRepo.save(new PaperJournalEntity(UUID.fromString(entry.id()), id, d.symbol(),
                entry.sessionDate(), "LIVE_OPEN", d.mode(), d.direction(), d.grade(), d.score(),
                d.weightsVersion(), d.automation(), d.ts(), now, actor, note, payload));
        return json.read(payload);
    }

    private String mergeLiveMeta(PaperJournalEntry entry, String broker, String brokerOrderId, double lots) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = json.read(json.write(entry), Map.class);
        map.put("live", Map.of(
                "broker", broker,
                "broker_order_id", brokerOrderId == null ? "" : brokerOrderId,
                "lots", lots,
                "channel", "P5_MICRO"
        ));
        return json.write(map);
    }

    private LiveBroker resolveBroker(String name) {
        String n = name == null ? "none" : name.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "sim" -> simBroker;
            case "mt5" -> mt5Broker;
            default -> throw new ConflictException("LIVE_BROKER", "Unknown broker: " + name);
        };
    }

    private static UUID parse(String decisionId) {
        try {
            return UUID.fromString(decisionId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("DECISION_NOT_FOUND", "invalid decision_id: " + decisionId);
        }
    }
}
