package com.delena.tradingportal.engine.ict;

import com.delena.tradingportal.model.IctSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** EQH/EQL clustering and round-number magnet pools (DEEP-ALGORITHMS §2.5–2.6). */
final class LiquidityPools {

    /** Max EQH and max EQL pools retained after merge (nearest to mid). */
    static final int MAX_EQUAL_PER_SIDE = 3;

    private LiquidityPools() {
    }

    static List<IctSnapshot.Pool> detectEqualLevels(List<IctSnapshot.Swing> swings, double eps) {
        return detectEqualLevels(swings, eps, Double.NaN, MAX_EQUAL_PER_SIDE);
    }

    /**
     * Cluster swing highs/lows within {@code eps}, merge near-duplicate averages, then keep at most
     * {@code maxPerSide} EQH and EQL nearest to {@code midPrice} (or first N if mid is NaN).
     */
    static List<IctSnapshot.Pool> detectEqualLevels(List<IctSnapshot.Swing> swings, double eps,
                                                    double midPrice, int maxPerSide) {
        List<Double> highs = swings.stream()
                .filter(s -> "high".equals(s.type()))
                .map(IctSnapshot.Swing::price)
                .toList();
        List<Double> lows = swings.stream()
                .filter(s -> "low".equals(s.type()))
                .map(IctSnapshot.Swing::price)
                .toList();
        List<IctSnapshot.Pool> out = new ArrayList<>();
        out.addAll(selectNearest(mergeNearPools(clustersToPools(highs, "EQH", "high", eps), eps),
                midPrice, maxPerSide));
        out.addAll(selectNearest(mergeNearPools(clustersToPools(lows, "EQL", "low", eps), eps),
                midPrice, maxPerSide));
        return out;
    }

    static List<IctSnapshot.Pool> roundNumberPools(double midPrice, double atr) {
        double range = Math.max(2.0 * atr, 50.0);
        List<IctSnapshot.Pool> out = new ArrayList<>();
        addNearestRound(out, midPrice, range, 5, "ROUND_5");
        addNearestRound(out, midPrice, range, 10, "ROUND_10");
        addNearestRound(out, midPrice, range, 50, "ROUND_50");
        addNearestRound(out, midPrice, range, 100, "ROUND_100");
        return out;
    }

    private static List<IctSnapshot.Pool> clustersToPools(List<Double> prices, String name, String side, double eps) {
        List<IctSnapshot.Pool> pools = new ArrayList<>();
        for (List<Double> cluster : clusterPrices(prices, eps)) {
            if (cluster.size() >= 2) {
                double avg = cluster.stream().mapToDouble(d -> d).average().orElse(0);
                pools.add(new IctSnapshot.Pool(name, round(avg), side));
            }
        }
        return pools;
    }

    /** Merge pools of the same name whose prices are within {@code eps}. */
    static List<IctSnapshot.Pool> mergeNearPools(List<IctSnapshot.Pool> pools, double eps) {
        if (pools.isEmpty()) {
            return List.of();
        }
        List<IctSnapshot.Pool> sorted = new ArrayList<>(pools);
        sorted.sort(Comparator.comparingDouble(IctSnapshot.Pool::price));
        List<IctSnapshot.Pool> merged = new ArrayList<>();
        IctSnapshot.Pool cur = sorted.get(0);
        int count = 1;
        double sum = cur.price();
        for (int i = 1; i < sorted.size(); i++) {
            IctSnapshot.Pool next = sorted.get(i);
            if (next.name().equals(cur.name()) && Math.abs(next.price() - sum / count) <= eps) {
                sum += next.price();
                count++;
            } else {
                merged.add(new IctSnapshot.Pool(cur.name(), round(sum / count), cur.side()));
                cur = next;
                sum = next.price();
                count = 1;
            }
        }
        merged.add(new IctSnapshot.Pool(cur.name(), round(sum / count), cur.side()));
        return merged;
    }

    private static List<IctSnapshot.Pool> selectNearest(List<IctSnapshot.Pool> pools, double mid, int max) {
        if (pools.size() <= max) {
            return pools;
        }
        if (Double.isNaN(mid)) {
            return pools.subList(0, max);
        }
        return pools.stream()
                .sorted(Comparator.comparingDouble(p -> Math.abs(p.price() - mid)))
                .limit(max)
                .sorted(Comparator.comparingDouble(IctSnapshot.Pool::price))
                .toList();
    }

    private static List<List<Double>> clusterPrices(List<Double> prices, double eps) {
        List<Double> unassigned = new ArrayList<>(prices);
        List<List<Double>> clusters = new ArrayList<>();
        while (!unassigned.isEmpty()) {
            List<Double> cluster = new ArrayList<>();
            cluster.add(unassigned.remove(0));
            boolean expanded;
            do {
                expanded = false;
                double avg = cluster.stream().mapToDouble(d -> d).average().orElse(0);
                for (int i = unassigned.size() - 1; i >= 0; i--) {
                    if (Math.abs(unassigned.get(i) - avg) <= eps) {
                        cluster.add(unassigned.remove(i));
                        expanded = true;
                    }
                }
            } while (expanded);
            clusters.add(cluster);
        }
        return clusters;
    }

    private static void addNearestRound(List<IctSnapshot.Pool> out, double mid, double range, int step, String name) {
        double nearest = Math.round(mid / step) * (double) step;
        if (Math.abs(nearest - mid) <= range) {
            String side = nearest >= mid ? "high" : "low";
            out.add(new IctSnapshot.Pool(name, round(nearest), side));
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
