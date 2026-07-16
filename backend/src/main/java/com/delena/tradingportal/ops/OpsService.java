package com.delena.tradingportal.ops;

import com.delena.tradingportal.config.TradingProperties;
import com.delena.tradingportal.persistence.ConfluenceDecisionRepository;
import com.delena.tradingportal.persistence.PaperJournalRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** PREPROD soak metrics and weights-version audit (paper-only instrumentation). */
@Service
public class OpsService {

    public static final int SOAK_MIN_DECISIONS = 30;
    public static final int SOAK_MIN_SESSION_DAYS = 10;

    private final PaperJournalRepository journalRepo;
    private final ConfluenceDecisionRepository decisionRepo;
    private final TradingProperties props;

    public OpsService(PaperJournalRepository journalRepo, ConfluenceDecisionRepository decisionRepo,
                      TradingProperties props) {
        this.journalRepo = journalRepo;
        this.decisionRepo = decisionRepo;
        this.props = props;
    }

    public SoakMetrics soak() {
        long journalDecisionCount = journalRepo.countDistinctDecisionIds();
        long distinctSessionDays = journalRepo.countDistinctSessionDays();
        long paperOpenCount = journalRepo.countByStatus("PAPER_OPEN");
        long alertedCount = journalRepo.countByStatus("ALERTED");
        long rejectedCount = journalRepo.countByStatus("REJECTED");
        List<String> weightsVersions = journalRepo.findDistinctWeightsVersions();
        boolean soakMet = journalDecisionCount >= SOAK_MIN_DECISIONS
                || distinctSessionDays >= SOAK_MIN_SESSION_DAYS;
        return new SoakMetrics(journalDecisionCount, distinctSessionDays, paperOpenCount, alertedCount,
                rejectedCount, weightsVersions, new SoakTarget(SOAK_MIN_DECISIONS, SOAK_MIN_SESSION_DAYS), soakMet);
    }

    public WeightsAudit weights() {
        Set<String> seen = new LinkedHashSet<>();
        seen.add(props.getConfluence().getWeightsVersion());
        seen.addAll(decisionRepo.findDistinctWeightsVersions());
        seen.addAll(journalRepo.findDistinctWeightsVersions());
        return new WeightsAudit(props.getConfluence().getWeightsVersion(), new ArrayList<>(seen));
    }

    public record SoakTarget(int minDecisions, int minSessionDays) {
    }

    public record SoakMetrics(long journalDecisionCount, long distinctSessionDays, long paperOpenCount,
                              long alertedCount, long rejectedCount, List<String> weightsVersions,
                              SoakTarget soakTarget, boolean soakMet) {
    }

    public record WeightsAudit(String configuredWeightsVersion, List<String> distinctVersionsSeen) {
    }
}
