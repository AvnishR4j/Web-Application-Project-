package com.campus.booking.report;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pure arithmetic for the utilization report, kept free of Spring and MongoDB so it can be tested directly. */
public final class UtilizationCalculator {

    private UtilizationCalculator() {
    }

    /** Minutes of [start, end) that fall inside [from, to). */
    public static long overlapMinutes(Instant start, Instant end, Instant from, Instant to) {
        Instant s = start.isAfter(from) ? start : from;
        Instant e = end.isBefore(to) ? end : to;
        return e.isAfter(s) ? Duration.between(s, e).toMinutes() : 0;
    }

    /**
     * Bookable minutes of a resource inside [from, to): its daily opening hours on each day in the campus zone,
     * or every minute when it has no opening hours.
     */
    public static long openMinutes(String openTime, String closeTime, Instant from, Instant to, ZoneId zone) {
        if (!to.isAfter(from)) {
            return 0;
        }
        if (openTime == null || closeTime == null) {
            return Duration.between(from, to).toMinutes();
        }
        LocalTime open = LocalTime.parse(openTime);
        LocalTime close = LocalTime.parse(closeTime);
        long total = 0;
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        while (!day.isAfter(last)) {
            Instant dayOpen = ZonedDateTime.of(day, open, zone).toInstant();
            Instant dayClose = ZonedDateTime.of(day, close, zone).toInstant();
            total += overlapMinutes(dayOpen, dayClose, from, to);
            day = day.plusDays(1);
        }
        return total;
    }

    /** Percentage with one decimal, or null when nothing was bookable. */
    public static Double percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }
}
