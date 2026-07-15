package com.delena.tradingportal.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * America/New_York time helper (DST-aware). Every engine gate depends on a correct NY clock
 * (ICT killzones, Gann session origin, session_date). Per AUTOMATION-PIPELINE Stage A and the
 * ohlc-bar contract, ny_time must never silently fall back to UTC.
 *
 * <p>All methods are pure. The self-check ({@link #selfCheck()}) is exposed via
 * {@code GET /api/health/ny-time} and is covered by unit tests, including DST transition fixtures.
 */
public final class NyTime {

    public static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private NyTime() {
    }

    /** NY-zoned view of a UTC instant. */
    public static ZonedDateTime toNy(Instant instant) {
        return instant.atZone(NY_ZONE);
    }

    /** Wall-clock offset in effect at this instant in New York (-4 during EDT, -5 during EST). */
    public static ZoneOffset offsetAt(Instant instant) {
        return NY_ZONE.getRules().getOffset(instant);
    }

    /**
     * NY trading day for an instant. Uses the NY calendar date; this is the session_date id used
     * across the confluence framework and journal.
     */
    public static LocalDate sessionDate(Instant instant) {
        return toNy(instant).toLocalDate();
    }

    /**
     * Active killzone at the instant, expressed in NY local time, or {@code null} when none.
     * Windows (NY local): LONDON_OPEN 02:00-05:00, NY_OPEN 07:00-10:00, NY_AFTERNOON 13:30-16:00.
     */
    public static String killzone(Instant instant) {
        LocalTime t = toNy(instant).toLocalTime();
        if (!t.isBefore(LocalTime.of(2, 0)) && t.isBefore(LocalTime.of(5, 0))) {
            return "LONDON_OPEN";
        }
        if (!t.isBefore(LocalTime.of(7, 0)) && t.isBefore(LocalTime.of(10, 0))) {
            return "NY_OPEN";
        }
        if (!t.isBefore(LocalTime.of(13, 30)) && t.isBefore(LocalTime.of(16, 0))) {
            return "NY_AFTERNOON";
        }
        return null;
    }

    /** Midday suppression window (NY local 11:30-13:00): new entries forced to grade &lt;= B. */
    public static boolean isMidday(Instant instant) {
        LocalTime t = toNy(instant).toLocalTime();
        return !t.isBefore(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0));
    }

    // ---- Self-check (health + tests) ---------------------------------------------------------

    public record Check(String name, boolean pass, String detail) {
    }

    /**
     * Deterministic DST correctness fixtures. All must pass; any failure is a hard fail
     * (never a silent UTC fallback), per the ny-time contract.
     */
    public static List<Check> selfCheck() {
        List<Check> out = new ArrayList<>();
        out.add(offsetCheck("offset_utc_minus_4", Instant.parse("2026-07-15T12:00:00Z"), -4));
        out.add(offsetCheck("offset_utc_minus_5", Instant.parse("2026-01-15T12:00:00Z"), -5));
        // Spring forward 2026: US DST begins 2026-03-08 at 02:00 local (07:00Z).
        out.add(offsetCheck("dst_spring_forward_2026_before", Instant.parse("2026-03-08T06:59:00Z"), -5));
        out.add(offsetCheck("dst_spring_forward_2026_after", Instant.parse("2026-03-08T07:00:00Z"), -4));
        // Fall back 2026: DST ends 2026-11-01 at 02:00 local (06:00Z EDT -> 01:00 local EST).
        out.add(offsetCheck("dst_fall_back_2026_before", Instant.parse("2026-11-01T05:59:00Z"), -4));
        out.add(offsetCheck("dst_fall_back_2026_after", Instant.parse("2026-11-01T06:00:00Z"), -5));
        return out;
    }

    public static boolean allPass(List<Check> checks) {
        return checks.stream().allMatch(Check::pass);
    }

    private static Check offsetCheck(String name, Instant instant, int expectedHours) {
        ZoneOffset actual = offsetAt(instant);
        int expectedSeconds = expectedHours * 3600;
        boolean pass = actual.getTotalSeconds() == expectedSeconds;
        String detail = "expected UTC" + expectedHours + ", got " + actual.getId()
                + " for " + instant;
        return new Check(name, pass, detail);
    }
}
