package com.delena.tradingportal.pipeline;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.config.TradingProperties;
import com.delena.tradingportal.engine.confluence.ConfluenceEngine;
import com.delena.tradingportal.engine.gann.GannConfig;
import com.delena.tradingportal.engine.gann.GannEngine;
import com.delena.tradingportal.engine.ict.IctConfig;
import com.delena.tradingportal.engine.ict.IctEngine;
import com.delena.tradingportal.engine.risk.RiskGate;
import com.delena.tradingportal.market.MarketDataService;
import com.delena.tradingportal.model.ConfluenceDecision;
import com.delena.tradingportal.model.GannSnapshot;
import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.model.PaperJournalEntry;
import com.delena.tradingportal.model.RiskVerdict;
import com.delena.tradingportal.news.NewsCalendarService;
import com.delena.tradingportal.paper.PaperTradingService;
import com.delena.tradingportal.persistence.ConfluenceDecisionEntity;
import com.delena.tradingportal.persistence.ConfluenceDecisionRepository;
import com.delena.tradingportal.persistence.GannSnapshotEntity;
import com.delena.tradingportal.persistence.GannSnapshotRepository;
import com.delena.tradingportal.persistence.IctSnapshotEntity;
import com.delena.tradingportal.persistence.IctSnapshotRepository;
import com.delena.tradingportal.persistence.PaperJournalEntity;
import com.delena.tradingportal.persistence.PaperJournalRepository;
import com.delena.tradingportal.persistence.RiskVerdictEntity;
import com.delena.tradingportal.persistence.RiskVerdictRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates Stage B..E of the automation pipeline: run ICT + Gann engines over stored OHLC,
 * fuse into a graded {@link ConfluenceDecision}, run the Risk Gate, and persist every snapshot plus
 * an initial journal row (ALERTED when risk ok, else REJECTED with paper=null). No live execution.
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);
    private static final String AUTO_CONFIRM_ACTOR = "system:auto-confirm-a-plus";

    private final MarketDataService market;
    private final IctEngine ictEngine;
    private final GannEngine gannEngine;
    private final ConfluenceEngine confluenceEngine;
    private final RiskGate riskGate;
    private final NewsCalendarService newsCalendar;
    private final PaperTradingService paperTrading;
    private final ConfluenceDecisionRepository decisionRepo;
    private final IctSnapshotRepository ictRepo;
    private final GannSnapshotRepository gannRepo;
    private final RiskVerdictRepository riskRepo;
    private final PaperJournalRepository journalRepo;
    private final Json json;
    private final TradingProperties props;

    public PipelineService(MarketDataService market, IctEngine ictEngine, GannEngine gannEngine,
                           ConfluenceEngine confluenceEngine, RiskGate riskGate,
                           NewsCalendarService newsCalendar, PaperTradingService paperTrading,
                           ConfluenceDecisionRepository decisionRepo, IctSnapshotRepository ictRepo,
                           GannSnapshotRepository gannRepo, RiskVerdictRepository riskRepo,
                           PaperJournalRepository journalRepo, Json json, TradingProperties props) {
        this.market = market;
        this.ictEngine = ictEngine;
        this.gannEngine = gannEngine;
        this.confluenceEngine = confluenceEngine;
        this.riskGate = riskGate;
        this.newsCalendar = newsCalendar;
        this.paperTrading = paperTrading;
        this.decisionRepo = decisionRepo;
        this.ictRepo = ictRepo;
        this.gannRepo = gannRepo;
        this.riskRepo = riskRepo;
        this.journalRepo = journalRepo;
        this.json = json;
        this.props = props;
    }

    /** Recompute the latest decision from stored OHLC and persist all artifacts. */
    @Transactional
    public Optional<ConfluenceDecision> recompute() {
        return recompute(Instant.now());
    }

    /**
     * Recompute a decision using OHLC bars with {@code ts <= asof}. Used for journal replay and
     * point-in-time ops. Does not invent OHLC — only filters stored candles.
     */
    @Transactional
    public Optional<ConfluenceDecision> recompute(Instant asof) {
        var m5 = market.barsUpTo("M5", asof);
        var m15 = market.barsUpTo("M15", asof);
        var h1 = market.barsUpTo("H1", asof);
        var h4 = market.barsUpTo("H4", asof);
        var d1 = market.barsUpTo("D1", asof);
        if (m5.isEmpty() && m15.isEmpty()) {
            log.warn("recompute skipped: no OHLC candles present up to {}", asof);
            return Optional.empty();
        }
        Instant effectiveAsof = latestTs(m5, m15, asof);

        boolean newsVeto = newsCalendar.isVeto(effectiveAsof);
        IctSnapshot ict = ictEngine.compute(h4, h1, m15, m5, effectiveAsof, IctConfig.defaults());
        GannSnapshot gann = gannEngine.compute(m5, d1, effectiveAsof, GannConfig.defaults(), "NY_OPEN");
        ConfluenceDecision decision = confluenceEngine.decide(ict, gann, effectiveAsof, newsVeto,
                props.getConfluence().getWeightsVersion());

        int openPositions = (int) journalRepo.countByStatus("PAPER_OPEN");
        RiskVerdict risk = riskGate.verdict(decision, openPositions, 0.0);

        persist(ict, gann, decision, risk);
        maybeAutoConfirm(decision, risk);
        log.info("Decision {} grade={} mode={} dir={} agreement={} riskOk={} newsVeto={} (ictQ={}, gannQ={})",
                decision.id(), decision.grade(), decision.mode(), decision.direction(),
                decision.agreement(), risk.ok(), newsVeto, ict.quality(), gann.quality());
        return Optional.of(decision);
    }

    private void maybeAutoConfirm(ConfluenceDecision decision, RiskVerdict risk) {
        if (!props.getPaper().isAutoConfirmAPlus()) {
            return;
        }
        if (paperTrading.autoOpenIfEligible(decision, risk, AUTO_CONFIRM_ACTOR)) {
            log.info("A+ auto-confirm opened paper for decision {}", decision.id());
        }
    }

    private void persist(IctSnapshot ict, GannSnapshot gann, ConfluenceDecision d, RiskVerdict risk) {
        ictRepo.save(new IctSnapshotEntity(UUID.randomUUID(), ict.symbol(), ict.asof(),
                ict.killzone(), ict.quality(), json.write(ict)));
        gannRepo.save(new GannSnapshotEntity(UUID.randomUUID(), gann.symbol(), gann.asof(),
                gann.killzone(), gann.gannBias(), gann.quality(), json.write(gann)));

        UUID decisionId = UUID.fromString(d.id());
        decisionRepo.save(new ConfluenceDecisionEntity(decisionId, d.symbol(), d.ts(), d.mode(),
                d.direction(), d.grade(), d.score(), d.agreement(), d.automation(),
                d.weightsVersion(), d.engines().ictRef(), d.engines().gannRef(), json.write(d)));

        riskRepo.save(new RiskVerdictEntity(UUID.randomUUID(), decisionId, risk.ts(), risk.ok(),
                risk.size(), risk.riskPerTradePct(), risk.dailyLossR(), risk.openPositions(),
                json.write(risk.denyReasons()), json.write(risk.checks()), json.write(risk)));

        String status = risk.ok() ? "ALERTED" : "REJECTED";
        PaperJournalEntry entry = new PaperJournalEntry(UUID.randomUUID().toString(), d.id(), d.symbol(),
                NyTime.sessionDate(d.ts()), status, d.mode(), d.direction(), d.grade(), d.score(),
                d.reasons(), d.weightsVersion(), d.entry(), d.stop(), d.targets(), d.invalidIf(),
                d.automation(), new PaperJournalEntry.RiskSummary(risk.ok(), risk.size(), risk.denyReasons()),
                d.ts(), null, null, null, null);
        journalRepo.save(new PaperJournalEntity(UUID.fromString(entry.id()), decisionId, d.symbol(),
                entry.sessionDate(), status, d.mode(), d.direction(), d.grade(), d.score(),
                d.weightsVersion(), d.automation(), d.ts(), null, null, null, json.write(entry)));
    }

    private static Instant latestTs(List<OhlcBar> m5, List<OhlcBar> m15, Instant ceiling) {
        List<OhlcBar> src = m5.isEmpty() ? m15 : m5;
        Instant barTs = src.get(src.size() - 1).ts();
        return barTs.isAfter(ceiling) ? ceiling : barTs;
    }
}
