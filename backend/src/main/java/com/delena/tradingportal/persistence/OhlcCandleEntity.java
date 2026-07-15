package com.delena.tradingportal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ohlc_candle")
public class OhlcCandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String tf;
    private Instant ts;

    @Column(name = "ny_time")
    private Instant nyTime;

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    @Column(name = "broker_time")
    private Instant brokerTime;

    protected OhlcCandleEntity() {
    }

    public OhlcCandleEntity(String symbol, String tf, Instant ts, Instant nyTime,
                            double open, double high, double low, double close, double volume) {
        this.symbol = symbol;
        this.tf = tf;
        this.ts = ts;
        this.nyTime = nyTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTf() {
        return tf;
    }

    public Instant getTs() {
        return ts;
    }

    public Instant getNyTime() {
        return nyTime;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }

    public Instant getBrokerTime() {
        return brokerTime;
    }
}
