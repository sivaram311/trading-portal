package com.delena.tradingportal.model;

import java.time.Instant;

/** API/DTO view of one OHLC bar (docs/contracts/schemas/ohlc-bar.json). */
public record OhlcBar(
        String symbol,
        String tf,
        Instant ts,
        Instant nyTime,
        double open,
        double high,
        double low,
        double close,
        double volume,
        Instant brokerTime
) {
}
