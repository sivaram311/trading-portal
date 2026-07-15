package com.delena.tradingportal.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NyTimeTest {

    @Test
    void selfCheckAllPassIncludingDstTransitions() {
        var checks = NyTime.selfCheck();
        assertTrue(NyTime.allPass(checks), () -> "failed: " + checks);
        // Sanity: fixtures cover both a spring-forward and a fall-back case.
        assertTrue(checks.stream().anyMatch(c -> c.name().startsWith("dst_spring_forward")));
        assertTrue(checks.stream().anyMatch(c -> c.name().startsWith("dst_fall_back")));
    }

    @Test
    void offsetIsMinus4InSummerAndMinus5InWinter() {
        assertEquals(ZoneOffset.ofHours(-4), NyTime.offsetAt(Instant.parse("2026-07-15T12:00:00Z")));
        assertEquals(ZoneOffset.ofHours(-5), NyTime.offsetAt(Instant.parse("2026-01-15T12:00:00Z")));
    }

    @Test
    void dstSpringForwardBoundary2026() {
        // US DST begins 2026-03-08 02:00 local == 07:00Z.
        assertEquals(ZoneOffset.ofHours(-5), NyTime.offsetAt(Instant.parse("2026-03-08T06:59:00Z")));
        assertEquals(ZoneOffset.ofHours(-4), NyTime.offsetAt(Instant.parse("2026-03-08T07:00:00Z")));
    }

    @Test
    void dstFallBackBoundary2026() {
        // DST ends 2026-11-01 02:00 local (EDT) == 06:00Z.
        assertEquals(ZoneOffset.ofHours(-4), NyTime.offsetAt(Instant.parse("2026-11-01T05:59:00Z")));
        assertEquals(ZoneOffset.ofHours(-5), NyTime.offsetAt(Instant.parse("2026-11-01T06:00:00Z")));
    }

    @Test
    void killzoneWindows() {
        // 2026-07-15 is EDT (UTC-4). NY 08:30 == 12:30Z => NY_OPEN.
        assertEquals("NY_OPEN", NyTime.killzone(Instant.parse("2026-07-15T12:30:00Z")));
        // NY 03:00 == 07:00Z => LONDON_OPEN.
        assertEquals("LONDON_OPEN", NyTime.killzone(Instant.parse("2026-07-15T07:00:00Z")));
        // NY 14:00 == 18:00Z => NY_AFTERNOON.
        assertEquals("NY_AFTERNOON", NyTime.killzone(Instant.parse("2026-07-15T18:00:00Z")));
        // NY 12:00 == 16:00Z => midday, no killzone.
        assertNull(NyTime.killzone(Instant.parse("2026-07-15T16:00:00Z")));
        assertTrue(NyTime.isMidday(Instant.parse("2026-07-15T16:00:00Z")));
    }
}
