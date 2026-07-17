package com.delena.tradingportal.ops;

import com.delena.tradingportal.config.TradingProperties;
import com.delena.tradingportal.persistence.ConfluenceDecisionEntity;
import com.delena.tradingportal.persistence.ConfluenceDecisionRepository;
import com.delena.tradingportal.persistence.OhlcCandleEntity;
import com.delena.tradingportal.persistence.OhlcCandleRepository;
import com.delena.tradingportal.persistence.PaperJournalRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** PREPROD soak metrics, weights audit, and fleet observability (paper-only). */
@Service
public class OpsService {

    public static final int SOAK_MIN_DECISIONS = 30;
    public static final int SOAK_MIN_SESSION_DAYS = 10;
    private static final String SYMBOL = "XAUUSD";
    private static final List<String> OBS_TFS = List.of("M1", "M5", "M15", "H1", "H4", "D1");

    private final PaperJournalRepository journalRepo;
    private final ConfluenceDecisionRepository decisionRepo;
    private final OhlcCandleRepository ohlcRepo;
    private final TradingProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public OpsService(PaperJournalRepository journalRepo, ConfluenceDecisionRepository decisionRepo,
                      OhlcCandleRepository ohlcRepo, TradingProperties props) {
        this.journalRepo = journalRepo;
        this.decisionRepo = decisionRepo;
        this.ohlcRepo = ohlcRepo;
        this.props = props;
    }

    public SoakMetrics soak() {
        long journalDecisionCount = journalRepo.countDistinctDecisionIds();
        long distinctSessionDays = journalRepo.countDistinctSessionDays();
        long paperOpenCount = journalRepo.countOpenPositions();
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

    /** Aggregated operator status: soak + OHLC freshness + ingest probe + live-guard. */
    public FleetStatus status() {
        return status(true, "KILL_SWITCH");
    }

    public FleetStatus status(boolean killSwitchEngaged, String liveGateDenyReason) {
        Instant now = Instant.now();
        SoakMetrics soak = soak();
        WeightsAudit weights = weights();
        List<OhlcTfStatus> ohlc = new ArrayList<>();
        boolean anyStale = false;
        long staleAfter = props.getOps().getOhlcStaleAfterSeconds();
        for (String tf : OBS_TFS) {
            long count = ohlcRepo.countBySymbolAndTf(SYMBOL, tf);
            Optional<OhlcCandleEntity> top = ohlcRepo.findTopBySymbolAndTfOrderByTsDesc(SYMBOL, tf);
            Instant latest = top.map(OhlcCandleEntity::getTs).orElse(null);
            Long ageSec = latest == null ? null : Duration.between(latest, now).getSeconds();
            boolean stale = count == 0 || ageSec == null || ageSec > staleAfter;
            if (List.of("M1", "M5", "M15", "H1").contains(tf) && stale) {
                anyStale = true;
            }
            ohlc.add(new OhlcTfStatus(tf, count, latest, ageSec, stale));
        }
        LatestDecision latestDecision = decisionRepo.findTopByOrderByTsDesc()
                .map(d -> new LatestDecision(d.getId().toString(), d.getGrade(), d.getMode(),
                        d.getDirection(), d.getTs(), d.getWeightsVersion()))
                .orElse(null);
        IngestProbe ingest = probeIngest();
        boolean liveEnabled = props.getExec().isLiveEnabled();
        String level = liveEnabled ? "CRIT"
                : (!ingest.reachable() && !props.getOps().getIngestHealthUrl().isBlank()) ? "WARN"
                : anyStale ? "WARN"
                : "OK";
        return new FleetStatus(now, level, liveEnabled, killSwitchEngaged, liveGateDenyReason,
                soak, weights, ohlc, latestDecision, ingest,
                props.getOps().getIngestHealthUrl(), staleAfter,
                props.getExec().getBroker(), props.getExec().getAllowedProfiles(),
                props.getStyle().name(), FeatureInventory.current());
    }

    private IngestProbe probeIngest() {
        String url = props.getOps().getIngestHealthUrl();
        if (url == null || url.isBlank()) {
            return new IngestProbe(false, false, null, "not_configured");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() >= 200 && res.statusCode() < 300
                    && res.body() != null
                    && res.body().contains("\"status\"");
            String snippet = res.body() == null ? ""
                    : res.body().substring(0, Math.min(240, res.body().length()));
            return new IngestProbe(true, ok, res.statusCode(), snippet);
        } catch (Exception e) {
            return new IngestProbe(true, false, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public record SoakTarget(int minDecisions, int minSessionDays) {
    }

    public record SoakMetrics(long journalDecisionCount, long distinctSessionDays, long paperOpenCount,
                              long alertedCount, long rejectedCount, List<String> weightsVersions,
                              SoakTarget soakTarget, boolean soakMet) {
    }

    public record WeightsAudit(String configuredWeightsVersion, List<String> distinctVersionsSeen) {
    }

    public record OhlcTfStatus(String tf, long barCount, Instant latestTs, Long ageSeconds, boolean stale) {
    }

    public record LatestDecision(String id, String grade, String mode, String direction,
                                 Instant ts, String weightsVersion) {
    }

    public record IngestProbe(boolean configured, boolean reachable, Integer httpStatus, String detail) {
    }

    public record FleetStatus(Instant checkedAt, String level, boolean liveEnabled,
                              boolean killSwitchEngaged, String liveGateDenyReason,
                              SoakMetrics soak, WeightsAudit weights, List<OhlcTfStatus> ohlc,
                              LatestDecision latestDecision, IngestProbe ingest,
                              String ingestHealthUrl, long ohlcStaleAfterSeconds,
                              String liveBroker, String liveAllowedProfiles,
                              String tradingStyle, FeatureInventory features) {
    }

    /**
     * Honest deep-algo / P5 surface map for operators (IMPLEMENTED | PARTIAL | MISSING).
     * Source of truth for “are all features implemented?” — not marketing.
     */
    public record FeatureInventory(
            String ote,
            String eqhEqlRounds,
            String styleProfiles,
            String marketQualityGate,
            String positionManager,
            String journalMfeMae,
            String backtester,
            String walkForwardMonteCarlo,
            String breakerIfvgUnicorn,
            String dxySmt,
            String p5MicroLive,
            String uiOverlays,
            String mitigationBlock,
            String multiDayGann,
            String pyramiding,
            String analyticsDashboardUi,
            String styleSelectorUi
    ) {
        static FeatureInventory current() {
            return new FeatureInventory(
                    "IMPLEMENTED",
                    "IMPLEMENTED",
                    "IMPLEMENTED_BACKEND",
                    "IMPLEMENTED",
                    "IMPLEMENTED_NO_PYRAMIDING",
                    "IMPLEMENTED_BACKEND",
                    "IMPLEMENTED_API",
                    "IMPLEMENTED_API",
                    "IMPLEMENTED",
                    "IMPLEMENTED",
                    "CODED_FAIL_CLOSED",
                    "IMPLEMENTED_PRICE_RAIL",
                    "MISSING",
                    "MISSING",
                    "MISSING_BY_DESIGN",
                    "PARTIAL_CSV_API",
                    "MISSING");
        }
    }
}
