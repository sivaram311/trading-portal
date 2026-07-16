package com.delena.tradingportal.web;

import com.delena.tradingportal.live.LiveTradingService;
import com.delena.tradingportal.web.dto.Dtos.ConfirmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class LiveController {

    private final LiveTradingService live;

    public LiveController(LiveTradingService live) {
        this.live = live;
    }

    @GetMapping("/api/live/gate")
    public Map<String, Object> gate() {
        var v = live.gateStatus();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", v.ok());
        out.put("deny_reason", v.denyReason());
        out.put("broker", v.broker());
        out.put("kill_switch_engaged", live.isKillSwitchEngaged());
        return out;
    }

    @PostMapping("/api/live/confirm")
    public JsonNode confirm(@Valid @RequestBody ConfirmRequest req, Authentication auth) {
        String actor = auth != null ? auth.getName() : "unknown";
        return live.confirm(req.decisionId(), req.note(), actor);
    }
}
