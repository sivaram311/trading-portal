package com.delena.tradingportal.engine.smt;

import com.delena.tradingportal.model.OhlcBar;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * DXY Smart Money Technique divergence vs XAUUSD structure (DEEP-ALGORITHMS §4.2).
 * Confirmation only — missing DXY data fails soft (does not deny trades).
 */
@Component
public class SmtDetector {

    public record SmtSignal(String bias, boolean divergent, String detail) {}

    private static final int SWING_N_M15 = 2;
    private static final int SWING_N_H1 = 3;

    /**
     * Detect SMT divergence using M15 bars when both series have enough swings; otherwise H1.
     */
    public SmtSignal detect(List<OhlcBar> goldM15, List<OhlcBar> goldH1,
                            List<OhlcBar> dxyM15, List<OhlcBar> dxyH1) {
        if (dxyM15 == null || dxyM15.isEmpty()) {
            if (dxyH1 == null || dxyH1.isEmpty()) {
                return new SmtSignal("none", false, "NO_DXY_DATA");
            }
            return detectOnTf(goldH1, dxyH1, SWING_N_H1, "H1");
        }
        SmtSignal m15 = detectOnTf(goldM15, dxyM15, SWING_N_M15, "M15");
        if (m15.divergent()) {
            return m15;
        }
        if (dxyH1 != null && !dxyH1.isEmpty()) {
            return detectOnTf(goldH1, dxyH1, SWING_N_H1, "H1");
        }
        return m15;
    }

    private SmtSignal detectOnTf(List<OhlcBar> gold, List<OhlcBar> dxy, int swingN, String tf) {
        if (gold == null || gold.isEmpty()) {
            return new SmtSignal("none", false, "NO_GOLD_DATA_" + tf);
        }
        Structure goldStruct = structure(gold, swingN);
        Structure dxyStruct = structure(dxy, swingN);
        if (!goldStruct.valid() || !dxyStruct.valid()) {
            return new SmtSignal("none", false, "INSUFFICIENT_SWINGS_" + tf);
        }

        boolean goldStrong = (goldStruct.hh() && dxyStruct.lh()) || (goldStruct.hl() && dxyStruct.ll());
        boolean goldWeak = (goldStruct.lh() && dxyStruct.hh()) || (goldStruct.ll() && dxyStruct.hl());

        if (goldStrong) {
            return new SmtSignal("gold_strong", true,
                    detail(tf, goldStruct, dxyStruct, "GOLD_STRONG"));
        }
        if (goldWeak) {
            return new SmtSignal("gold_weak", true,
                    detail(tf, goldStruct, dxyStruct, "GOLD_WEAK"));
        }
        return new SmtSignal("none", false,
                detail(tf, goldStruct, dxyStruct, "NO_DIVERGENCE"));
    }

    private static String detail(String tf, Structure gold, Structure dxy, String outcome) {
        return outcome + "|" + tf
                + "|gold=" + gold.label()
                + "|dxy=" + dxy.label();
    }

    private record Structure(boolean hh, boolean hl, boolean lh, boolean ll, boolean valid) {
        String label() {
            if (hh && hl) {
                return "HH_HL";
            }
            if (lh && ll) {
                return "LH_LL";
            }
            if (hh) {
                return "HH";
            }
            if (hl) {
                return "HL";
            }
            if (lh) {
                return "LH";
            }
            if (ll) {
                return "LL";
            }
            return "FLAT";
        }
    }

    private static Structure structure(List<OhlcBar> bars, int n) {
        List<Swing> swings = swings(bars, n);
        List<Swing> highs = swings.stream().filter(s -> "high".equals(s.type())).toList();
        List<Swing> lows = swings.stream().filter(s -> "low".equals(s.type())).toList();
        if (highs.size() < 2 || lows.size() < 2) {
            return new Structure(false, false, false, false, false);
        }
        double lastHigh = highs.get(highs.size() - 1).price();
        double prevHigh = highs.get(highs.size() - 2).price();
        double lastLow = lows.get(lows.size() - 1).price();
        double prevLow = lows.get(lows.size() - 2).price();
        return new Structure(
                lastHigh > prevHigh,
                lastLow > prevLow,
                lastHigh < prevHigh,
                lastLow < prevLow,
                true);
    }

    private record Swing(String type, double price) {}

    private static List<Swing> swings(List<OhlcBar> bars, int n) {
        List<Swing> out = new ArrayList<>();
        if (bars.size() < 2 * n + 1) {
            return out;
        }
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
                out.add(new Swing("high", round(bars.get(i).high())));
            }
            if (sl) {
                out.add(new Swing("low", round(bars.get(i).low())));
            }
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
