package com.delena.tradingportal.market;

import com.delena.tradingportal.model.OhlcBar;
import com.delena.tradingportal.persistence.OhlcCandleEntity;
import com.delena.tradingportal.persistence.OhlcCandleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Read access to stored XAUUSD OHLC candles. Engines consume {@link OhlcBar} views only. */
@Service
public class MarketDataService {

    public static final String SYMBOL = "XAUUSD";

    private final OhlcCandleRepository repository;

    public MarketDataService(OhlcCandleRepository repository) {
        this.repository = repository;
    }

    public List<OhlcBar> bars(String tf) {
        return repository.findBySymbolAndTfOrderByTsAsc(SYMBOL, tf).stream().map(MarketDataService::toBar).toList();
    }

    /** Bars for a timeframe with {@code ts <= asof} (replay / point-in-time compute). */
    public List<OhlcBar> barsUpTo(String tf, Instant asof) {
        return bars(tf).stream().filter(b -> !b.ts().isAfter(asof)).toList();
    }

    public List<OhlcBar> bars(String tf, Instant from, Instant to) {
        return repository.findBySymbolAndTfAndTsBetweenOrderByTsAsc(SYMBOL, tf, from, to)
                .stream().map(MarketDataService::toBar).toList();
    }

    public long count(String tf) {
        return repository.countBySymbolAndTf(SYMBOL, tf);
    }

    public static OhlcBar toBar(OhlcCandleEntity e) {
        return new OhlcBar(e.getSymbol(), e.getTf(), e.getTs(), e.getNyTime(),
                e.getOpen(), e.getHigh(), e.getLow(), e.getClose(), e.getVolume(), e.getBrokerTime());
    }
}
