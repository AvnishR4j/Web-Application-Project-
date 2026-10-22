package com.campus.booking.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class UtilizationCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Instant ist(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(IST).toInstant();
    }

    @Test
    void overlapCountsOnlyTheSharedPart() {
        Instant from = ist("2030-01-10T00:00");
        Instant to = ist("2030-01-11T00:00");
        assertEquals(120, UtilizationCalculator.overlapMinutes(ist("2030-01-10T10:00"), ist("2030-01-10T12:00"), from, to));
        assertEquals(60, UtilizationCalculator.overlapMinutes(ist("2030-01-09T23:00"), ist("2030-01-10T01:00"), from, to));
        assertEquals(0, UtilizationCalculator.overlapMinutes(ist("2030-01-11T10:00"), ist("2030-01-11T12:00"), from, to));
    }

    @Test
    void openMinutesFollowDailyHours() {
        // Two full days, open 08:00 to 20:00: 2 x 12 hours.
        assertEquals(2 * 12 * 60, UtilizationCalculator.openMinutes("08:00", "20:00",
                ist("2030-01-10T00:00"), ist("2030-01-12T00:00"), IST));
    }

    @Test
    void partialDaysAreClipped() {
        // From 18:00 on day one to 10:00 on day two: 2 hours + 2 hours of opening time.
        assertEquals(4 * 60, UtilizationCalculator.openMinutes("08:00", "20:00",
                ist("2030-01-10T18:00"), ist("2030-01-11T10:00"), IST));
    }

    @Test
    void noOpeningHoursMeansAllDay() {
        assertEquals(24 * 60, UtilizationCalculator.openMinutes(null, null,
                ist("2030-01-10T00:00"), ist("2030-01-11T00:00"), IST));
    }

    @Test
    void percentRoundsToOneDecimalAndHandlesZero() {
        assertEquals(16.7, UtilizationCalculator.percent(120, 720));
        assertEquals(100.0, UtilizationCalculator.percent(5, 5));
        assertNull(UtilizationCalculator.percent(5, 0));
    }

    @Test
    void emptyRangeHasNoOpenTime() {
        Instant t = ist("2030-01-10T10:00");
        assertEquals(0, UtilizationCalculator.openMinutes("08:00", "20:00", t, t, IST));
    }
}
