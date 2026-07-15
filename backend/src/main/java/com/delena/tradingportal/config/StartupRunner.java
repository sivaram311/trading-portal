package com.delena.tradingportal.config;

import com.delena.tradingportal.persistence.ConfluenceDecisionRepository;
import com.delena.tradingportal.pipeline.PipelineService;
import com.delena.tradingportal.seed.SeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup: seed synthetic OHLC if the table is empty (feature-flagged) and compute the first
 * confluence decision so the API serves a graded decision immediately. Failures are logged, not
 * fatal — health endpoints must stay reachable even if seeding/compute fails.
 */
@Component
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final SeedService seedService;
    private final PipelineService pipelineService;
    private final ConfluenceDecisionRepository decisionRepo;
    private final TradingProperties props;

    public StartupRunner(SeedService seedService, PipelineService pipelineService,
                         ConfluenceDecisionRepository decisionRepo, TradingProperties props) {
        this.seedService = seedService;
        this.pipelineService = pipelineService;
        this.decisionRepo = decisionRepo;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (props.getSeed().isEnabled()) {
                int seeded = seedService.seedIfEmpty();
                if (seeded > 0) {
                    log.info("Startup seed inserted {} candles", seeded);
                }
            }
            if (decisionRepo.count() == 0) {
                pipelineService.recompute().ifPresent(d ->
                        log.info("Startup decision computed: {} grade={}", d.id(), d.grade()));
            }
        } catch (Exception e) {
            log.error("Startup seed/compute failed (API stays up): {}", e.getMessage(), e);
        }
    }
}
