package com.delena.tradingportal.web;

import com.delena.tradingportal.market.MarketDataService;
import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.web.ApiExceptions.NotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
public class MarketController {

    private static final Set<String> TIMEFRAMES = Set.of("M1", "M5", "M15", "H1", "H4", "D1");

    private final MarketDataService market;

    public MarketController(MarketDataService market) {
        this.market = market;
    }

    @GetMapping("/api/market/xauusd/ohlc")
    public List<OhlcBar> ohlc(@RequestParam("tf") String tf,
                              @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                              @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (!TIMEFRAMES.contains(tf)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tf: " + tf);
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be <= to");
        }
        return market.bars(tf, from, to);
    }
}
