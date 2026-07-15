package com.delena.tradingportal.web;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.persistence.ConfluenceDecisionRepository;
import com.delena.tradingportal.pipeline.PipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfluenceController {

    private final ConfluenceDecisionRepository decisionRepo;
    private final PipelineService pipeline;
    private final Json json;

    public ConfluenceController(ConfluenceDecisionRepository decisionRepo, PipelineService pipeline, Json json) {
        this.decisionRepo = decisionRepo;
        this.pipeline = pipeline;
        this.json = json;
    }

    @GetMapping("/api/confluence/decision")
    public ResponseEntity<JsonNode> latest() {
        var latest = decisionRepo.findTopByOrderByTsDesc();
        if (latest.isEmpty()) {
            // Compute on demand from stored OHLC (per hire brief: "on startup or GET confluence").
            pipeline.recompute();
            latest = decisionRepo.findTopByOrderByTsDesc();
        }
        return latest
                .<ResponseEntity<JsonNode>>map(e -> ResponseEntity.ok(json.read(e.getPayload())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
