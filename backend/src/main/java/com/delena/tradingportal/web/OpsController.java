package com.delena.tradingportal.web;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.ops.OpsService;
import com.delena.tradingportal.pipeline.PipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsService ops;
    private final PipelineService pipeline;
    private final Json json;

    public OpsController(OpsService ops, PipelineService pipeline, Json json) {
        this.ops = ops;
        this.pipeline = pipeline;
        this.json = json;
    }

    @GetMapping("/soak")
    public OpsService.SoakMetrics soak() {
        return ops.soak();
    }

    @GetMapping("/weights")
    public OpsService.WeightsAudit weights() {
        return ops.weights();
    }

    /** Fleet observability: soak + OHLC freshness + ingest health probe + live-exec guard. */
    @GetMapping("/status")
    public OpsService.FleetStatus status() {
        return ops.status();
    }

    /**
     * Recompute ICT + Gann + confluence from stored OHLC up to {@code asof} (default now).
     * Persists a new decision row and journal entry — same pipeline as startup recompute.
     */
    @PostMapping("/replay")
    public ResponseEntity<JsonNode> replay(@RequestParam(value = "asof", required = false) String asofParam) {
        Instant asof = Instant.now();
        if (asofParam != null && !asofParam.isBlank()) {
            try {
                asof = Instant.parse(asofParam);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "asof must be ISO-8601 instant, got: " + asofParam);
            }
        }
        return pipeline.recompute(asof)
                .map(d -> ResponseEntity.ok(json.read(json.write(d))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
