package com.delena.tradingportal.engine.gann;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.model.GannSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gann Cycle Engine (docs/algorithms/GANN-CYCLE-ENGINE.md). Pure compute over stored OHLC.
 * Emits {@link GannSnapshot}: 1x1 stretch/fan, Square-of-9 proximity, time-squaring milestones,
 * session cycle checkpoint, killzone, gann_bias hint, quality 0..5, reason codes.
 *
 * <p>Sign conventions: So9 even levels use (2n-1) offsets (frozen per §5.1); So9Level.dist is the
 * signed distance (price - level). angle.deviation = px - equilibrium; positive = stretched up.
 */
@Component
public class GannEngine {

    private static final String SYMBOL = "XAUUSD";
    private static final int N_FINE = 4;

    public GannSnapshot compute(List<OhlcBar> bars, List<OhlcBar> barsD1, Instant asof,
                                GannConfig cfg, String pivotSource) {
        if (bars == null || bars.isEmpty() || bars.stream().anyMatch(b -> b.nyTime() == null)) {
            return empty(asof, pivotSource, List.of("DATA_GAP"));
        }
        List<OhlcBar> window = bars.stream().filter(b -> !b.ts().isAfter(asof)).toList();
        if (window.size() < 3) {
            return empty(asof, pivotSource, List.of("DATA_GAP"));
        }

        List<String> reasons = new ArrayList<>();
        String source = pivotSource == null ? "NY_OPEN" : pivotSource;
        Pivot pivot = resolvePivot(window, barsD1, source, asof);
        if (pivot == null) {
            return empty(asof, source, List.of("DATA_GAP"));
        }
        reasons.add("PIVOT_" + pivot.source());

        double atr = atr(window, cfg.atrPeriod());
        OhlcBar last = window.get(window.size() - 1);
        double px = last.close();
        double minutes = Math.max(0, Duration.between(pivot.originNy(), asof).toMinutes());
        double t = minutes / Math.max(1, cfg.entryTfMinutes());

        double slope = atr;
        double equilibrium = pivot.price() + slope * t;
        double deviation = px - equilibrium;
        double stretch = atr > 0 ? deviation / atr : 0.0;
        boolean alert = Math.abs(stretch) >= cfg.atrAlert();
        String angleBias;
        if (stretch >= cfg.atrAlert()) {
            angleBias = "over_up";
            reasons.add("ANG_OVER_UP");
        } else if (stretch <= -cfg.atrAlert()) {
            angleBias = "over_down";
            reasons.add("ANG_OVER_DOWN");
        } else {
            angleBias = "balanced";
            reasons.add("ANG_BALANCED");
        }
        if (alert) {
            reasons.add("ANG_ALERT");
        }
        var fan = new GannSnapshot.Fan(round(equilibrium),
                round(pivot.price() + 2 * slope * t), round(pivot.price() + 0.5 * slope * t));
        var angle = new GannSnapshot.Angle(round(slope), round(equilibrium), round(deviation),
                round(stretch), angleBias, alert, fan);

        // --- Square of 9 ---
        List<GannSnapshot.So9Level> levels = buildSo9(pivot.price(), cfg, px);
        double tol = so9Tol(pivot.price(), cfg);
        GannSnapshot.So9Level nearest = levels.stream()
                .min(Comparator.comparingDouble(l -> Math.abs(l.dist()))).orElse(null);
        boolean atLevel = nearest != null && Math.abs(nearest.dist()) <= tol;
        if (atLevel) {
            switch (nearest.kind()) {
                case "fine" -> reasons.add("SO9_FINE");
                case "odd" -> reasons.add("SO9_ODD");
                case "even" -> reasons.add("SO9_EVEN");
                default -> { }
            }
        }
        var so9 = new GannSnapshot.So9(levels, atLevel, nearest);

        // --- Time squaring ---
        double priceMove = Math.abs(px - pivot.price());
        List<GannSnapshot.Milestone> milestones = new ArrayList<>();
        boolean anyNearSquare = false;
        for (int m : cfg.milestonesMin()) {
            double target = cfg.timeScale() * m;
            boolean nearTime = Math.abs(minutes - m) <= cfg.nearTimeMin();
            boolean nearPrice = Math.abs(priceMove - target) <= tol;
            boolean balance = Math.abs(priceMove - cfg.timeScale() * minutes) <= tol;
            boolean nearSquare = nearTime || nearPrice || balance;
            if (nearSquare) {
                anyNearSquare = true;
                reasons.add("TSQ_" + m);
            }
            milestones.add(new GannSnapshot.Milestone(m, round(target), nearTime, nearPrice, nearSquare));
        }
        if (anyNearSquare) {
            reasons.add("TSQ_NEAR");
        }
        var timeSquare = new GannSnapshot.TimeSquare(round(minutes), round(priceMove), milestones, anyNearSquare);

        // --- Cycles ---
        double frac = minutes / cfg.sessionLenMin();
        String checkpoint = nearestFraction(frac, cfg.cycleFractions());
        if (checkpoint != null) {
            reasons.add(checkpoint);
        }
        var cycles = new GannSnapshot.Cycles(round(frac), checkpoint);

        // --- Killzone + filters ---
        String killzone = NyTime.killzone(asof);
        if (killzone != null) {
            reasons.add("KZ_" + killzone);
        }
        boolean volSpike = volumeSpike(window, cfg.volSpikeMult());
        boolean revCandle = reversalCandle(last);
        if (volSpike) {
            reasons.add("VOL_SPIKE");
        }
        if (revCandle) {
            reasons.add("REV_CANDLE");
        }
        var filters = new GannSnapshot.Filters(volSpike, revCandle, null);

        String gannBias = inferBias(angleBias, atLevel, anyNearSquare, killzone != null);
        int quality = score(alert, atLevel, anyNearSquare, killzone != null, volSpike || revCandle);

        return new GannSnapshot(SYMBOL, asof, toPivotModel(pivot), angle, so9, timeSquare, cycles,
                killzone, gannBias, quality, reasons, filters);
    }

    // ------------------------------------------------------------------ helpers

    private GannSnapshot empty(Instant asof, String source, List<String> reasons) {
        var pivot = new GannSnapshot.Pivot(source == null ? "NY_OPEN" : source, 0, asof);
        var angle = new GannSnapshot.Angle(0, 0, 0, 0, "balanced", false, new GannSnapshot.Fan(0, 0, 0));
        var so9 = new GannSnapshot.So9(List.of(), false, null);
        var ts = new GannSnapshot.TimeSquare(0, 0, List.of(), false);
        var cycles = new GannSnapshot.Cycles(0, null);
        return new GannSnapshot(SYMBOL, asof, pivot, angle, so9, ts, cycles, null, "neutral", 0, reasons,
                new GannSnapshot.Filters(false, false, null));
    }

    private record Pivot(String source, double price, Instant originNy) {
    }

    private static GannSnapshot.Pivot toPivotModel(Pivot p) {
        return new GannSnapshot.Pivot(p.source(), round(p.price()), p.originNy());
    }

    private static Pivot resolvePivot(List<OhlcBar> window, List<OhlcBar> barsD1, String source, Instant asof) {
        LocalDate today = NyTime.sessionDate(asof);
        List<OhlcBar> session = window.stream()
                .filter(b -> NyTime.sessionDate(b.ts()).equals(today)).toList();
        switch (source) {
            case "LONDON_OPEN" -> {
                OhlcBar b = firstAtOrAfter(session, LocalTime.of(2, 0));
                if (b != null) {
                    return new Pivot("LONDON_OPEN", b.open(), b.ts());
                }
            }
            case "NY_HIGH" -> {
                if (!session.isEmpty()) {
                    OhlcBar hi = session.stream().max(Comparator.comparingDouble(OhlcBar::high)).orElse(null);
                    return new Pivot("NY_HIGH", hi.high(), hi.ts());
                }
            }
            case "NY_LOW" -> {
                if (!session.isEmpty()) {
                    OhlcBar lo = session.stream().min(Comparator.comparingDouble(OhlcBar::low)).orElse(null);
                    return new Pivot("NY_LOW", lo.low(), lo.ts());
                }
            }
            case "PDH", "PDL", "PREV_CLOSE" -> {
                Pivot p = priorDayPivot(window, barsD1, source, today);
                if (p != null) {
                    return p;
                }
            }
            default -> { }
        }
        // Default / fallback: NY_OPEN (first bar at/after 07:00 NY, else first session bar).
        OhlcBar nyOpen = firstAtOrAfter(session, LocalTime.of(7, 0));
        if (nyOpen == null && !session.isEmpty()) {
            nyOpen = session.get(0);
        }
        if (nyOpen == null) {
            nyOpen = window.get(0);
        }
        return new Pivot("NY_OPEN", nyOpen.open(), nyOpen.ts());
    }

    private static Pivot priorDayPivot(List<OhlcBar> window, List<OhlcBar> barsD1, String source, LocalDate today) {
        List<OhlcBar> src = (barsD1 != null && !barsD1.isEmpty()) ? barsD1 : window;
        List<OhlcBar> prior = src.stream().filter(b -> NyTime.sessionDate(b.ts()).isBefore(today)).toList();
        if (prior.isEmpty()) {
            return null;
        }
        LocalDate prevDay = prior.stream().map(b -> NyTime.sessionDate(b.ts())).max(LocalDate::compareTo).orElse(null);
        List<OhlcBar> prevBars = prior.stream().filter(b -> NyTime.sessionDate(b.ts()).equals(prevDay)).toList();
        return switch (source) {
            case "PDH" -> new Pivot("PDH", prevBars.stream().mapToDouble(OhlcBar::high).max().orElse(0),
                    prevBars.get(prevBars.size() - 1).ts());
            case "PDL" -> new Pivot("PDL", prevBars.stream().mapToDouble(OhlcBar::low).min().orElse(0),
                    prevBars.get(prevBars.size() - 1).ts());
            default -> new Pivot("PREV_CLOSE", prevBars.get(prevBars.size() - 1).close(),
                    prevBars.get(prevBars.size() - 1).ts());
        };
    }

    private static OhlcBar firstAtOrAfter(List<OhlcBar> session, LocalTime t) {
        for (OhlcBar b : session) {
            if (!NyTime.toNy(b.ts()).toLocalTime().isBefore(t)) {
                return b;
            }
        }
        return null;
    }

    private static List<GannSnapshot.So9Level> buildSo9(double p, GannConfig cfg, double px) {
        List<GannSnapshot.So9Level> levels = new ArrayList<>();
        double root = Math.sqrt(Math.max(0, p));
        for (double step : cfg.so9FineSteps()) {
            for (int n = 1; n < N_FINE; n++) {
                levels.add(level("fine", n * step, sq(root + n * step), px));
                levels.add(level("fine", -n * step, sq(root - n * step), px));
            }
        }
        for (int n = 1; n <= cfg.so9OddNMax(); n++) {
            levels.add(level("odd", 2 * n, sq(root + 2 * n), px));
            levels.add(level("odd", -2 * n, sq(root - 2 * n), px));
            levels.add(level("even", 2 * n - 1, sq(root + (2 * n - 1)), px));
            levels.add(level("even", -(2 * n - 1), sq(root - (2 * n - 1)), px));
        }
        levels.sort(Comparator.comparingDouble(GannSnapshot.So9Level::price));
        return levels;
    }

    private static GannSnapshot.So9Level level(String kind, double k, double price, double px) {
        return new GannSnapshot.So9Level(kind, k, round(price), round(px - price));
    }

    private static double sq(double x) {
        return x * x;
    }

    private static double so9Tol(double price, GannConfig cfg) {
        return Math.max(cfg.so9NearPts(), price * cfg.so9NearPct());
    }

    private static String nearestFraction(double frac, List<Double> fractions) {
        double best = Double.MAX_VALUE;
        Double bestFrac = null;
        for (double f : fractions) {
            double d = Math.abs(frac - f);
            if (d < best && d <= 0.06) {
                best = d;
                bestFrac = f;
            }
        }
        if (bestFrac == null) {
            return null;
        }
        if (bestFrac == 0.125) {
            return "CYC_1_8";
        }
        if (bestFrac == 0.25) {
            return "CYC_1_4";
        }
        if (bestFrac == 0.333) {
            return "CYC_1_3";
        }
        if (bestFrac == 0.5) {
            return "CYC_1_2";
        }
        if (bestFrac == 0.75) {
            return "CYC_3_4";
        }
        if (bestFrac == 0.875) {
            return "CYC_7_8";
        }
        return null;
    }

    private static boolean volumeSpike(List<OhlcBar> bars, double mult) {
        if (bars.size() < 5) {
            return false;
        }
        int n = Math.min(20, bars.size() - 1);
        double sum = 0;
        for (int i = bars.size() - 1 - n; i < bars.size() - 1; i++) {
            sum += bars.get(i).volume();
        }
        double avg = sum / n;
        return avg > 0 && bars.get(bars.size() - 1).volume() >= mult * avg;
    }

    private static boolean reversalCandle(OhlcBar b) {
        double range = b.high() - b.low();
        if (range <= 0) {
            return false;
        }
        double body = Math.abs(b.close() - b.open());
        double upperWick = b.high() - Math.max(b.open(), b.close());
        double lowerWick = Math.min(b.open(), b.close()) - b.low();
        return (upperWick >= 2 * body || lowerWick >= 2 * body) && body / range < 0.4;
    }

    private static String inferBias(String angleBias, boolean atLevel, boolean anyNearSquare, boolean kz) {
        if (angleBias.equals("over_up") && (atLevel || anyNearSquare)) {
            return "fade_short";
        }
        if (angleBias.equals("over_down") && (atLevel || anyNearSquare)) {
            return "fade_long";
        }
        return "neutral";
    }

    private static int score(boolean alert, boolean atLevel, boolean anyNearSquare, boolean kz, boolean volOrRev) {
        int q = 0;
        if (alert) {
            q += 2;
        }
        if (atLevel) {
            q += 1;
        }
        if (anyNearSquare) {
            q += 1;
        }
        if (kz) {
            q += 1;
        }
        if (volOrRev) {
            q += 1;
        }
        return Math.max(0, Math.min(5, q));
    }

    static double atr(List<OhlcBar> bars, int period) {
        if (bars.size() < 2) {
            return 0;
        }
        int n = Math.min(period, bars.size() - 1);
        double sum = 0;
        for (int i = bars.size() - n; i < bars.size(); i++) {
            OhlcBar cur = bars.get(i);
            OhlcBar prev = bars.get(i - 1);
            double tr = Math.max(cur.high() - cur.low(),
                    Math.max(Math.abs(cur.high() - prev.close()), Math.abs(cur.low() - prev.close())));
            sum += tr;
        }
        return sum / n;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
