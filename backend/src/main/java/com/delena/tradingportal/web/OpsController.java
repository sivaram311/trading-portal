package com.delena.tradingportal.web;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.live.LiveTradingService;
import com.delena.tradingportal.ops.OpsService;
import com.delena.tradingportal.pipeline.PipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsService ops;
    private final PipelineService pipeline;
    private final LiveTradingService live;
    private final Json json;

    public OpsController(OpsService ops, PipelineService pipeline, LiveTradingService live, Json json) {
        this.ops = ops;
        this.pipeline = pipeline;
        this.live = live;
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
        return ops.status(live.isKillSwitchEngaged(), live.gateStatus().denyReason());
    }

    @GetMapping("/kill-switch")
    public Map<String, Object> killSwitchGet() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engaged", live.isKillSwitchEngaged());
        out.put("live_gate", live.gateStatus());
        return out;
    }

    public record KillSwitchRequest(Boolean engaged) {
    }

    /** Engage or clear the P5 kill switch. Engaged=true blocks all live submits. */
    @PostMapping("/kill-switch")
    public Map<String, Object> killSwitchSet(@RequestBody KillSwitchRequest body) {
        if (body == null || body.engaged() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "engaged boolean required");
        }
        live.setKillSwitchEngaged(body.engaged());
        return killSwitchGet();
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
