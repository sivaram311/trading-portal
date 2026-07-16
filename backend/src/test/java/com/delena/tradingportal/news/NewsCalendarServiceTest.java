package com.delena.tradingportal.news;

import com.delena.tradingportal.config.TradingProperties;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsCalendarServiceTest {

    @Test
    void absoluteWindowVetoesInsideRange() {
        TradingProperties props = new TradingProperties();
        TradingProperties.BlackoutWindow w = new TradingProperties.BlackoutWindow();
        w.setStart(Instant.parse("2026-07-16T13:25:00Z"));
        w.setEnd(Instant.parse("2026-07-16T14:00:00Z"));
        props.getNews().setBlackouts(List.of(w));

        NewsCalendarService svc = new NewsCalendarService(props);
        assertTrue(svc.isVeto(Instant.parse("2026-07-16T13:30:00Z")));
        assertFalse(svc.isVeto(Instant.parse("2026-07-16T14:00:00Z")));
        assertFalse(svc.isVeto(Instant.parse("2026-07-16T13:24:59Z")));
    }

    @Test
    void nyLocalWindowVetoesOnMatchingWeekday() {
        TradingProperties props = new TradingProperties();
        TradingProperties.BlackoutWindow w = new TradingProperties.BlackoutWindow();
        w.setWeekday(DayOfWeek.WEDNESDAY);
        w.setNyStart(LocalTime.of(8, 25));
        w.setNyEnd(LocalTime.of(8, 35));
        props.getNews().setBlackouts(List.of(w));

        NewsCalendarService svc = new NewsCalendarService(props);
        // 2026-07-15 is Wednesday; 12:30Z = 08:30 EDT
        assertTrue(svc.isVeto(Instant.parse("2026-07-15T12:30:00Z")));
        assertFalse(svc.isVeto(Instant.parse("2026-07-15T13:00:00Z")));
    }
}
