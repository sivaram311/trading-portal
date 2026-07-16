package com.delena.tradingportal.web;

import com.delena.tradingportal.paper.PaperTradingService;
import com.delena.tradingportal.web.dto.Dtos.CloseRequest;
import com.delena.tradingportal.web.dto.Dtos.ConfirmRequest;
import com.delena.tradingportal.web.dto.Dtos.DismissRequest;
import com.delena.tradingportal.web.dto.Dtos.JournalListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@RestController
public class PaperController {

    private final PaperTradingService paper;

    public PaperController(PaperTradingService paper) {
        this.paper = paper;
    }

    @PostMapping("/api/paper/confirm")
    public JsonNode confirm(@Valid @RequestBody ConfirmRequest req, Authentication auth) {
        return paper.confirm(req.decisionId(), req.note(), actor(auth));
    }

    @PostMapping("/api/paper/dismiss")
    public JsonNode dismiss(@Valid @RequestBody DismissRequest req, Authentication auth) {
        return paper.dismiss(req.decisionId(), req.reason(), actor(auth));
    }

    @PostMapping("/api/paper/close")
    public JsonNode close(@Valid @RequestBody CloseRequest req, Authentication auth) {
        double exit = req.exitPrice() != null ? req.exitPrice() : 0.0;
        String reason = req.exitReason() != null && !req.exitReason().isBlank() ? req.exitReason() : "MANUAL";
        return paper.close(req.decisionId(), reason, exit, actor(auth));
    }

    @GetMapping("/api/paper/journal")
    public JournalListResponse journal(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(value = "session_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sessionDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        int cappedLimit = Math.max(1, Math.min(500, limit));
        int safeOffset = Math.max(0, offset);
        var page = paper.journal(grade, mode, direction, status, sessionDate, from, to, cappedLimit, safeOffset);
        return new JournalListResponse(page.items(), page.total(), cappedLimit, safeOffset);
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "unknown";
    }
}
