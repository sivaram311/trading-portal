package com.delena.tradingportal.news;

import com.delena.tradingportal.common.NyTime;
import com.delena.tradingportal.config.TradingProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;

/** Config-driven news blackout calendar — fail-closed veto for high-impact windows. */
@Service
public class NewsCalendarService {

    private final TradingProperties props;

    public NewsCalendarService(TradingProperties props) {
        this.props = props;
    }

    /** Returns true when {@code asof} falls inside any configured blackout window. */
    public boolean isVeto(Instant asof) {
        if (asof == null) {
            return false;
        }
        for (TradingProperties.BlackoutWindow w : props.getNews().getBlackouts()) {
            if (inWindow(asof, w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inWindow(Instant asof, TradingProperties.BlackoutWindow w) {
        if (w.getStart() != null && w.getEnd() != null) {
            return !asof.isBefore(w.getStart()) && asof.isBefore(w.getEnd());
        }
        if (w.getNyStart() != null && w.getNyEnd() != null) {
            var ny = NyTime.toNy(asof);
            if (w.getWeekday() != null && ny.getDayOfWeek() != w.getWeekday()) {
                return false;
            }
            LocalTime t = ny.toLocalTime();
            LocalTime start = w.getNyStart();
            LocalTime end = w.getNyEnd();
            if (end.isAfter(start) || end.equals(start)) {
                return !t.isBefore(start) && t.isBefore(end);
            }
            // Overnight band (e.g. 22:00–02:00).
            return !t.isBefore(start) || t.isBefore(end);
        }
        return false;
    }
}
