package com.delena.tradingportal.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response DTOs for the HTTP surface (docs/contracts/openapi.yaml). */
public final class Dtos {

    private Dtos() {
    }

    public record ErrorResponse(String error, String message, Map<String, Object> details) {
        public ErrorResponse(String error, String message) {
            this(error, message, null);
        }
    }

    public record HealthChecks(Boolean db, Boolean ingest, Boolean mt5) {
    }

    public record HealthResponse(String status, Instant ts, Instant nyTime, HealthChecks checks) {
    }

    public record NyTimeCheck(String name, boolean pass, String detail) {
    }

    public record NyTimeHealthResponse(boolean ok, Instant utcNow, Instant nyTimeNow, List<NyTimeCheck> checks) {
    }

    public record ConfirmRequest(@NotBlank String decisionId, String note) {
    }

    public record DismissRequest(@NotBlank String decisionId, String reason) {
    }

    public record JournalListResponse(List<JsonNode> items, long total, int limit, int offset) {
    }
}
