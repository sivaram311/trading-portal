package com.delena.tradingportal.web;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.market.MarketDataService;
import com.delena.tradingportal.web.dto.Dtos.HealthChecks;
import com.delena.tradingportal.web.dto.Dtos.HealthResponse;
import com.delena.tradingportal.web.dto.Dtos.NyTimeCheck;
import com.delena.tradingportal.web.dto.Dtos.NyTimeHealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final MarketDataService market;

    public HealthController(MarketDataService market) {
        this.market = market;
    }

    @GetMapping
    public HealthResponse health() {
        boolean db;
        boolean ingest;
        try {
            long bars = market.count("M5");
            db = true;
            ingest = bars > 0; // MVP: any stored candles counts as ingest-present (synthetic seed included)
        } catch (Exception e) {
            db = false;
            ingest = false;
        }
        // No live MT5 connection in this slice — reported honestly, never fake-green.
        boolean mt5 = false;
        String status = !db ? "down" : (ingest ? "ok" : "degraded");
        Instant now = Instant.now();
        return new HealthResponse(status, now, now, new HealthChecks(db, ingest, mt5));
    }

    @GetMapping("/ny-time")
    public ResponseEntity<NyTimeHealthResponse> nyTime() {
        List<NyTime.Check> checks = NyTime.selfCheck();
        boolean ok = NyTime.allPass(checks);
        Instant now = Instant.now();
        List<NyTimeCheck> dto = checks.stream()
                .map(c -> new NyTimeCheck(c.name(), c.pass(), c.detail())).toList();
        var body = new NyTimeHealthResponse(ok, now, now, dto);
        return ok ? ResponseEntity.ok(body) : ResponseEntity.status(500).body(body);
    }
}
