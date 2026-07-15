package com.delena.tradingportal.web;

import com.delena.tradingportal.common.Json;
import com.delena.tradingportal.persistence.GannSnapshotRepository;
import com.delena.tradingportal.persistence.IctSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EngineController {

    private final IctSnapshotRepository ictRepo;
    private final GannSnapshotRepository gannRepo;
    private final Json json;

    public EngineController(IctSnapshotRepository ictRepo, GannSnapshotRepository gannRepo, Json json) {
        this.ictRepo = ictRepo;
        this.gannRepo = gannRepo;
        this.json = json;
    }

    @GetMapping("/api/engines/ict/snapshot")
    public ResponseEntity<JsonNode> ict(@RequestParam(value = "asof", required = false) String asof) {
        return ictRepo.findTopByOrderByAsofDesc()
                .<ResponseEntity<JsonNode>>map(e -> ResponseEntity.ok(json.read(e.getPayload())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/engines/gann/snapshot")
    public ResponseEntity<JsonNode> gann(@RequestParam(value = "asof", required = false) String asof,
                                         @RequestParam(value = "pivot_source", required = false) String pivotSource) {
        return gannRepo.findTopByOrderByAsofDesc()
                .<ResponseEntity<JsonNode>>map(e -> ResponseEntity.ok(json.read(e.getPayload())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
