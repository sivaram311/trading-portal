package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.model.IctSnapshot;
import com.delena.tradingportal.model.OhlcBar;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ICT Signal Engine (docs/algorithms/ICT-SIGNAL-ENGINE.md). Pure compute over stored OHLC —
 * never places orders. Emits an {@link IctSnapshot}: killzone, HTF premium/discount + bias,
 * structure (BOS/MSS), liquidity sweep/reclaim, OB/FVG zones, quality 0..5, and reason codes.
 */
@Component
public class IctEngine {

    private static final String SYMBOL = "XAUUSD";

    public IctSnapshot compute(List<OhlcBar> barsH1, List<OhlcBar> barsM15, List<OhlcBar> barsM5,
                               Instant asof, IctConfig cfg) {
        // Gate: every bar must carry ny_time; otherwise fail closed with DATA_GAP (never default to UTC).
        if (barsM15 == null || barsM15.isEmpty() || !allHaveNyTime(barsM15)) {
            return empty(asof, List.of("DATA_GAP"));
        }

        List<OhlcBar> m15 = upTo(barsM15, asof);
        List<OhlcBar> h1 = upTo(barsH1, asof);
        if (m15.size() < 5) {
            return empty(asof, List.of("DATA_GAP"));
        }

        List<String> reasons = new ArrayList<>();
        String killzone = NyTime.killzone(asof);
        if (killzone != null) {
            reasons.add("KZ_" + killzone);
        }

        // --- HTF dealing range + premium/discount + bias (H1) ---
        List<OhlcBar> htfSource = h1.isEmpty() ? m15 : h1;
        double dealingHigh = htfSource.stream().mapToDouble(OhlcBar::high).max().orElse(0);
        double dealingLow = htfSource.stream().mapToDouble(OhlcBar::low).min().orElse(0);
        double eq = (dealingHigh + dealingLow) / 2.0;
        double lastClose = m15.get(m15.size() - 1).close();
        String pd = premiumDiscount(lastClose, eq, cfg.equalEpsPts());
        String htfBias = biasFromStructure(htfSource, cfg.swingNH1());
        var htf = new IctSnapshot.Htf(round(dealingLow), round(dealingHigh), round(eq), pd, htfBias);
        switch (pd) {
            case "discount" -> reasons.add("PD_DISCOUNT");
            case "premium" -> reasons.add("PD_PREMIUM");
            default -> reasons.add("PD_EQ");
        }

        // --- Swings + displacement + structure (M15) ---
        List<IctSnapshot.Swing> swings = swings(m15, cfg.swingNM15());
        boolean displacement = displacement(m15, cfg.displacementBodyFrac());
        if (displacement) {
            reasons.add("DISP_OK");
        }

        // --- Liquidity pools + sweep/reclaim ---
        List<IctSnapshot.Pool> pools = buildPools(m15, asof);
        Sweep sweep = detectSweepReclaim(m15, pools, cfg.sweepReclaimBars());
        String liqEvent = sweep != null ? "sweep" : "none";
        String sweptPool = sweep != null ? sweep.pool().name() : null;
        boolean reclaim = sweep != null && sweep.reclaim();
        if (sweep != null && reclaim) {
            reasons.add("SWEEP_" + sweep.pool().name());
        }
        var liquidity = new IctSnapshot.Liquidity(pools, liqEvent, sweptPool, reclaim);

        // --- Structure event (BOS/MSS) ---
        // After a high-side sweep, intended trade is short (fade); low-side sweep -> long.
        String sweepIntent = sweep == null ? "none" : ("high".equals(sweep.pool().side()) ? "short" : "long");
        Structure structure = classifyBosMss(swings, m15, htfBias, sweepIntent, displacement);
        switch (structure.event() + "_" + structure.direction()) {
            case "MSS_short" -> reasons.add("MSS_BEAR");
            case "MSS_long" -> reasons.add("MSS_BULL");
            case "BOS_short" -> reasons.add("BOS_BEAR");
            case "BOS_long" -> reasons.add("BOS_BULL");
            default -> { }
        }

        // --- Zones: order blocks + FVGs + active entry ---
        List<IctSnapshot.Zone> fvgs = detectFvgs(m15, cfg);
        List<IctSnapshot.Zone> obs = deriveOrderBlocks(m15, structure.direction(), displacement);
        IctSnapshot.Zone activeEntry = selectEntry(obs, fvgs, structure.direction());
        if (activeEntry != null) {
            if ("OB".equals(activeEntry.type())) {
                reasons.add("OB_ACTIVE");
            } else {
                reasons.add("FVG_ACTIVE");
            }
        }
        var zones = new IctSnapshot.Zones(obs, fvgs, activeEntry);

        // HTF conflict flag (§5.3/§7): structure direction opposes H1 bias.
        if (!"none".equals(structure.direction()) && !"neutral".equals(htfBias)
                && !structure.direction().equals(htfBias)) {
            reasons.add("HTF_CONFLICT");
        }

        int quality = score(killzone, sweep, reclaim, structure, displacement, activeEntry, pd);

        var structureModel = new IctSnapshot.Structure(swings, structure.event(), structure.direction(), displacement);
        return new IctSnapshot(SYMBOL, asof, killzone, htf, structureModel, liquidity, zones,
                quality, reasons, new IctSnapshot.RawRefs(List.of()));
    }

    // ------------------------------------------------------------------ helpers

    private IctSnapshot empty(Instant asof, List<String> reasons) {
        var htf = new IctSnapshot.Htf(0, 0, 0, "EQ", "neutral");
        var structure = new IctSnapshot.Structure(List.of(), "none", "none", false);
        var liquidity = new IctSnapshot.Liquidity(List.of(), "none", null, false);
        var zones = new IctSnapshot.Zones(List.of(), List.of(), null);
        return new IctSnapshot(SYMBOL, asof, null, htf, structure, liquidity, zones, 0, reasons,
                new IctSnapshot.RawRefs(List.of()));
    }

    private static boolean allHaveNyTime(List<OhlcBar> bars) {
        return bars.stream().allMatch(b -> b.nyTime() != null);
    }

    private static List<OhlcBar> upTo(List<OhlcBar> bars, Instant asof) {
        if (bars == null) {
            return List.of();
        }
        return bars.stream().filter(b -> !b.ts().isAfter(asof)).toList();
    }

    private static String premiumDiscount(double px, double eq, double eps) {
        if (Math.abs(px - eq) <= eps) {
            return "EQ";
        }
        return px > eq ? "premium" : "discount";
    }

    /** HH/HL vs LH/LL over swing points; falls back to endpoint slope. */
    private static String biasFromStructure(List<OhlcBar> bars, int n) {
        List<IctSnapshot.Swing> sw = swings(bars, n);
        List<IctSnapshot.Swing> highs = sw.stream().filter(s -> s.type().equals("high")).toList();
        List<IctSnapshot.Swing> lows = sw.stream().filter(s -> s.type().equals("low")).toList();
        if (highs.size() >= 2 && lows.size() >= 2) {
            boolean hh = highs.get(highs.size() - 1).price() > highs.get(highs.size() - 2).price();
            boolean hl = lows.get(lows.size() - 1).price() > lows.get(lows.size() - 2).price();
            boolean lh = highs.get(highs.size() - 1).price() < highs.get(highs.size() - 2).price();
            boolean ll = lows.get(lows.size() - 1).price() < lows.get(lows.size() - 2).price();
            if (hh && hl) {
                return "long";
            }
            if (lh && ll) {
                return "short";
            }
        }
        double first = bars.get(0).close();
        double last = bars.get(bars.size() - 1).close();
        double band = Math.abs(last) * 0.001;
        if (last > first + band) {
            return "long";
        }
        if (last < first - band) {
            return "short";
        }
        return "neutral";
    }

    static List<IctSnapshot.Swing> swings(List<OhlcBar> bars, int n) {
        List<IctSnapshot.Swing> out = new ArrayList<>();
        for (int i = n; i < bars.size() - n; i++) {
            boolean sh = true;
            boolean sl = true;
            for (int j = 1; j <= n; j++) {
                if (!(bars.get(i).high() > bars.get(i - j).high() && bars.get(i).high() > bars.get(i + j).high())) {
                    sh = false;
                }
                if (!(bars.get(i).low() < bars.get(i - j).low() && bars.get(i).low() < bars.get(i + j).low())) {
                    sl = false;
                }
            }
            if (sh) {
                out.add(new IctSnapshot.Swing("high", round(bars.get(i).high()), bars.get(i).ts(), i));
            }
            if (sl) {
                out.add(new IctSnapshot.Swing("low", round(bars.get(i).low()), bars.get(i).ts(), i));
            }
        }
        return out;
    }

    private static boolean displacement(List<OhlcBar> bars, double bodyFrac) {
        int look = Math.min(4, bars.size());
        for (int i = bars.size() - look; i < bars.size(); i++) {
            OhlcBar b = bars.get(i);
            double range = b.high() - b.low();
            if (range <= 0) {
                continue;
            }
            double body = Math.abs(b.close() - b.open());
            if (body / range >= bodyFrac) {
                return true;
            }
        }
        return false;
    }

    private record Structure(String event, String direction) {
    }

    private static Structure classifyBosMss(List<IctSnapshot.Swing> swings, List<OhlcBar> bars,
                                            String priorBias, String sweepIntent, boolean disp) {
        double lastClose = bars.get(bars.size() - 1).close();
        double sh = swings.stream().filter(s -> s.type().equals("high"))
                .mapToDouble(IctSnapshot.Swing::price).max().orElse(Double.NaN);
        double sl = swings.stream().filter(s -> s.type().equals("low"))
                .mapToDouble(IctSnapshot.Swing::price).min().orElse(Double.NaN);

        // Reversal (MSS) preference when a sweep set an intent opposite to prior trend.
        if ("short".equals(sweepIntent) && !Double.isNaN(sl) && lastClose < sl && disp) {
            return new Structure("MSS", "short");
        }
        if ("long".equals(sweepIntent) && !Double.isNaN(sh) && lastClose > sh && disp) {
            return new Structure("MSS", "long");
        }
        // Continuation (BOS) in the direction of prior bias.
        if ("long".equals(priorBias) && !Double.isNaN(sh) && lastClose > sh && disp) {
            return new Structure("BOS", "long");
        }
        if ("short".equals(priorBias) && !Double.isNaN(sl) && lastClose < sl && disp) {
            return new Structure("BOS", "short");
        }
        return new Structure("none", "none");
    }

    private static List<IctSnapshot.Pool> buildPools(List<OhlcBar> m15, Instant asof) {
        List<IctSnapshot.Pool> pools = new ArrayList<>();
        LocalDate today = NyTime.sessionDate(asof);

        // Pre-NY-open range (Asia/London carryover) -> AHH / ALH.
        List<OhlcBar> preOpen = m15.stream()
                .filter(b -> NyTime.sessionDate(b.ts()).equals(today))
                .filter(b -> NyTime.toNy(b.ts()).toLocalTime().isBefore(LocalTime.of(7, 0)))
                .toList();
        if (!preOpen.isEmpty()) {
            pools.add(new IctSnapshot.Pool("AHH", round(preOpen.stream().mapToDouble(OhlcBar::high).max().orElse(0)), "high"));
            pools.add(new IctSnapshot.Pool("ALH", round(preOpen.stream().mapToDouble(OhlcBar::low).min().orElse(0)), "low"));
        }

        // Prior session high/low -> PDH / PDL.
        List<OhlcBar> prior = m15.stream()
                .filter(b -> NyTime.sessionDate(b.ts()).isBefore(today))
                .toList();
        if (!prior.isEmpty()) {
            LocalDate prevDay = prior.stream().map(b -> NyTime.sessionDate(b.ts()))
                    .max(LocalDate::compareTo).orElse(null);
            List<OhlcBar> prevBars = prior.stream()
                    .filter(b -> NyTime.sessionDate(b.ts()).equals(prevDay)).toList();
            pools.add(new IctSnapshot.Pool("PDH", round(prevBars.stream().mapToDouble(OhlcBar::high).max().orElse(0)), "high"));
            pools.add(new IctSnapshot.Pool("PDL", round(prevBars.stream().mapToDouble(OhlcBar::low).min().orElse(0)), "low"));
        }
        return pools;
    }

    private record Sweep(IctSnapshot.Pool pool, boolean reclaim, int bar) {
    }

    private static Sweep detectSweepReclaim(List<OhlcBar> bars, List<IctSnapshot.Pool> pools, int k) {
        int start = Math.max(0, bars.size() - 20);
        for (IctSnapshot.Pool pool : pools) {
            for (int i = start; i < bars.size(); i++) {
                OhlcBar b = bars.get(i);
                if ("high".equals(pool.side()) && b.high() > pool.price()) {
                    for (int j = i; j < Math.min(bars.size(), i + k + 1); j++) {
                        if (bars.get(j).close() < pool.price()) {
                            return new Sweep(pool, true, i);
                        }
                    }
                } else if ("low".equals(pool.side()) && b.low() < pool.price()) {
                    for (int j = i; j < Math.min(bars.size(), i + k + 1); j++) {
                        if (bars.get(j).close() > pool.price()) {
                            return new Sweep(pool, true, i);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<IctSnapshot.Zone> detectFvgs(List<OhlcBar> bars, IctConfig cfg) {
        List<IctSnapshot.Zone> out = new ArrayList<>();
        double atr = atr(bars, cfg.atrPeriod());
        double minSize = Math.max(cfg.minFvgPts(), cfg.minFvgAtrFrac() * atr);
        for (int i = 2; i < bars.size(); i++) {
            double low, high;
            String dir;
            if (bars.get(i).low() > bars.get(i - 2).high()) {
                low = bars.get(i - 2).high();
                high = bars.get(i).low();
                dir = "bull";
            } else if (bars.get(i).high() < bars.get(i - 2).low()) {
                low = bars.get(i).high();
                high = bars.get(i - 2).low();
                dir = "bear";
            } else {
                continue;
            }
            if (high - low >= minSize) {
                out.add(new IctSnapshot.Zone("FVG", dir, round(low), round(high), "fresh", bars.get(i).ts()));
            }
        }
        return out;
    }

    private static List<IctSnapshot.Zone> deriveOrderBlocks(List<OhlcBar> bars, String direction, boolean disp) {
        List<IctSnapshot.Zone> out = new ArrayList<>();
        if (!disp || "none".equals(direction) || bars.size() < 2) {
            return out;
        }
        // Last opposite-color candle before the current displacement leg.
        for (int i = bars.size() - 2; i >= Math.max(0, bars.size() - 8); i--) {
            OhlcBar b = bars.get(i);
            boolean bearishCandle = b.close() < b.open();
            boolean bullishCandle = b.close() > b.open();
            if ("short".equals(direction) && bullishCandle) {
                out.add(new IctSnapshot.Zone("OB", "bear", round(b.low()), round(b.high()), "fresh", b.ts()));
                break;
            }
            if ("long".equals(direction) && bearishCandle) {
                out.add(new IctSnapshot.Zone("OB", "bull", round(b.low()), round(b.high()), "fresh", b.ts()));
                break;
            }
        }
        return out;
    }

    private static IctSnapshot.Zone selectEntry(List<IctSnapshot.Zone> obs, List<IctSnapshot.Zone> fvgs, String direction) {
        String want = "short".equals(direction) ? "bear" : "long".equals(direction) ? "bull" : null;
        if (want == null) {
            return null;
        }
        for (IctSnapshot.Zone z : obs) {
            if (z.direction().equals(want)) {
                return z;
            }
        }
        for (IctSnapshot.Zone z : fvgs) {
            if (z.direction().equals(want)) {
                return z;
            }
        }
        return null;
    }

    private static int score(String killzone, Sweep sweep, boolean reclaim, Structure structure,
                             boolean displacement, IctSnapshot.Zone entry, String pd) {
        int q = 0;
        if (killzone != null) {
            q += 1;
        }
        if (sweep != null && reclaim) {
            q += 2;
        }
        if ("MSS".equals(structure.event())) {
            q += 2;
        } else if ("BOS".equals(structure.event())) {
            q += 1;
        }
        if (displacement) {
            q += 1;
        }
        if (entry != null) {
            q += 1;
        }
        boolean pdAligned = ("short".equals(structure.direction()) && "premium".equals(pd))
                || ("long".equals(structure.direction()) && "discount".equals(pd));
        if (pdAligned) {
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
