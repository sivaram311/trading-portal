package com.delena.tradingportal.seed;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.market.MarketDataService;
import com.delena.tradingportal.persistence.OhlcCandleEntity;
import com.delena.tradingportal.persistence.OhlcCandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds ~500 synthetic XAUUSD bars (M5 base, aggregated to M15/H1) around a recent NY session so
 * the engines produce a non-empty graded decision without a live MT5 feed. The final session is
 * scripted as a long "raid + reversal" (ICT sweeps session lows then a bullish MSS; Gann price
 * stretched below the rising 1x1 equilibrium at a time-square milestone => fade_long) yielding an
 * ALIGN_LONG Mode R setup inside the NY_OPEN killzone. Deterministic (no RNG).
 *
 * <p>This is clearly labelled synthetic data; it is never presented as real market history.
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final String SYMBOL = MarketDataService.SYMBOL;

    private final OhlcCandleRepository repository;

    public SeedService(OhlcCandleRepository repository) {
        this.repository = repository;
    }

    /** Latest M5 bar instant of the seeded set — the natural asof for the pipeline. */
    public Instant seededAsof() {
        LocalDate anchor = recentWeekday(NyTime.sessionDate(Instant.now()));
        return nyInstant(anchor, LocalTime.of(7, 45));
    }

    @Transactional
    public int seedIfEmpty() {
        long existing = repository.countBySymbolAndTf(SYMBOL, "M5");
        if (existing > 0) {
            return 0;
        }
        List<M5> path = buildM5Path();
        List<OhlcCandleEntity> all = new ArrayList<>();
        for (M5 b : path) {
            all.add(candle("M5", b.time(), b.open(), b.high(), b.low(), b.close(), b.volume()));
        }
        all.addAll(aggregate(path, "M15", 15));
        all.addAll(aggregate(path, "H1", 60));
        repository.saveAll(all);
        log.info("Seeded {} synthetic XAUUSD bars (M5={}, +M15/H1 aggregates); asof={}",
                all.size(), path.size(), seededAsof());
        return all.size();
    }

    // ------------------------------------------------------------------ path construction

    private record M5(Instant time, double open, double high, double low, double close, double volume) {
    }

    private List<M5> buildM5Path() {
        List<M5> out = new ArrayList<>();
        LocalDate finalDay = recentWeekday(NyTime.sessionDate(Instant.now()));

        double price = 1980.0;
        // Three calm prior sessions of gentle uptrend (establishes long HTF bias + PDH/PDL).
        for (int d = 3; d >= 1; d--) {
            LocalDate day = previousWeekday(finalDay, d);
            for (LocalTime t = LocalTime.of(0, 0); t.isBefore(LocalTime.of(17, 0)); t = t.plusMinutes(5)) {
                double open = price;
                double drift = 0.03;                       // slow uptrend
                double wobble = ((t.getMinute() / 5) % 2 == 0) ? 0.12 : -0.10;
                double close = open + drift + wobble;
                double high = Math.max(open, close) + 0.15;
                double low = Math.min(open, close) - 0.15;
                out.add(new M5(nyInstant(day, t), r(open), r(high), r(low), r(close), 100));
                price = close;
            }
        }

        // Final session.
        double base = price; // ~around 2000 after the drift
        // Pre-open (00:00-06:55): very calm so ATR stays low; carve a session low ~ base-3 near 04:00.
        for (LocalTime t = LocalTime.of(0, 0); t.isBefore(LocalTime.of(7, 0)); t = t.plusMinutes(5)) {
            double open = price;
            double target = base;
            if (t.isAfter(LocalTime.of(3, 30)) && t.isBefore(LocalTime.of(4, 30))) {
                target = base - 3.0; // pre-open low (pool just above the coming sweep)
            }
            double close = open + (target - open) * 0.25;
            double high = Math.max(open, close) + 0.10;
            double low = Math.min(open, close) - 0.10;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), 90));
            price = close;
        }

        double nyOpen = price; // pivot reference around base
        // NY open 07:00-07:20: sweep the session low (dip below pre-open ALH), calm-ish ranges.
        double[] dipCloses = {nyOpen - 1.5, nyOpen - 3.2, nyOpen - 4.0, nyOpen - 3.6};
        LocalTime t = LocalTime.of(7, 0);
        double prev = nyOpen;
        double sweepLow = nyOpen - 5.2; // spike low that runs stops below the pre-open low
        for (int i = 0; i < dipCloses.length; i++) {
            double open = prev;
            double close = dipCloses[i];
            double low = (i == 2) ? sweepLow : Math.min(open, close) - 0.4;
            double high = Math.max(open, close) + 0.4;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), 140 + i * 10));
            prev = close;
            t = t.plusMinutes(5);
        }

        // 07:20-07:45: bullish displacement reclaim + MSS above the intra-dip swing high.
        double[] upCloses = {prev + 2.0, prev + 4.5, prev + 7.5, prev + 9.5, prev + 11.0};
        double[] vols = {220, 260, 320, 300, 280};
        for (int i = 0; i < upCloses.length; i++) {
            double open = prev;
            double close = upCloses[i];
            double high = Math.max(open, close) + 0.5;
            double low = Math.min(open, close) - 0.3;
            out.add(new M5(nyInstant(finalDay, t), r(open), r(high), r(low), r(close), vols[i]));
            prev = close;
            t = t.plusMinutes(5);
        }
        return out;
    }

    // ------------------------------------------------------------------ aggregation + helpers

    private List<OhlcCandleEntity> aggregate(List<M5> m5, String tf, int minutes) {
        Map<Instant, List<M5>> buckets = new LinkedHashMap<>();
        for (M5 b : m5) {
            Instant bucket = floorTo(b.time(), minutes);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(b);
        }
        List<OhlcCandleEntity> out = new ArrayList<>();
        for (var e : buckets.entrySet()) {
            List<M5> bs = e.getValue();
            double open = bs.get(0).open();
            double close = bs.get(bs.size() - 1).close();
            double high = bs.stream().mapToDouble(M5::high).max().orElse(open);
            double low = bs.stream().mapToDouble(M5::low).min().orElse(open);
            double vol = bs.stream().mapToDouble(M5::volume).sum();
            out.add(candle(tf, e.getKey(), open, high, low, close, vol));
        }
        return out;
    }

    private Instant floorTo(Instant ts, int minutes) {
        // Floor within the NY-local calendar so buckets align to NY session boundaries.
        var ny = NyTime.toNy(ts);
        int totalMin = ny.getHour() * 60 + ny.getMinute();
        int floored = (totalMin / minutes) * minutes;
        LocalDateTime base = ny.toLocalDate().atStartOfDay().plusMinutes(floored);
        return base.atZone(NyTime.NY_ZONE).toInstant();
    }

    private OhlcCandleEntity candle(String tf, Instant ts, double o, double h, double l, double c, double v) {
        return new OhlcCandleEntity(SYMBOL, tf, ts, ts, r(o), r(h), r(l), r(c), v);
    }

    private static Instant nyInstant(LocalDate day, LocalTime time) {
        return LocalDateTime.of(day, time).atZone(NyTime.NY_ZONE).toInstant();
    }

    private static LocalDate recentWeekday(LocalDate d) {
        LocalDate x = d;
        while (x.getDayOfWeek() == DayOfWeek.SATURDAY || x.getDayOfWeek() == DayOfWeek.SUNDAY) {
            x = x.minusDays(1);
        }
        return x;
    }

    private static LocalDate previousWeekday(LocalDate from, int businessDaysBack) {
        LocalDate x = from;
        int moved = 0;
        while (moved < businessDaysBack) {
            x = x.minusDays(1);
            if (x.getDayOfWeek() != DayOfWeek.SATURDAY && x.getDayOfWeek() != DayOfWeek.SUNDAY) {
                moved++;
            }
        }
        return x;
    }

    private static double r(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @SuppressWarnings("unused")
    private static long minutesBetween(Instant a, Instant b) {
        return ChronoUnit.MINUTES.between(a, b);
    }
}
