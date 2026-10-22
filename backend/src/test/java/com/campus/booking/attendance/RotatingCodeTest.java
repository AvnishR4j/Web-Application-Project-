package com.campus.booking.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RotatingCodeTest {

    private final RotatingCode codes = new RotatingCode("unit-test-secret-unit-test-secret-1234");
    private static final Instant T0 = Instant.parse("2030-01-10T10:00:00Z"); // start of a 30-second window

    @Test
    void codesAreSixReadableCharacters() {
        String code = codes.current("event-1", T0);
        assertEquals(6, code.length());
        assertTrue(code.matches("[2-9A-HJKMNP-Z]{6}"), code);
    }

    @Test
    void sameWindowGivesSameCode() {
        assertEquals(codes.current("event-1", T0), codes.current("event-1", T0.plusSeconds(29)));
    }

    @Test
    void codeChangesEveryThirtySeconds() {
        assertNotEquals(codes.current("event-1", T0), codes.current("event-1", T0.plusSeconds(30)));
    }

    @Test
    void eachEventHasItsOwnCode() {
        assertNotEquals(codes.current("event-1", T0), codes.current("event-2", T0));
    }

    @Test
    void currentAndPreviousCodesAreAccepted() {
        String code = codes.current("event-1", T0);
        assertTrue(codes.matches("event-1", code, T0));
        assertTrue(codes.matches("event-1", code, T0.plusSeconds(45)), "one window of grace");
    }

    @Test
    void olderCodesAreRejected() {
        String code = codes.current("event-1", T0);
        assertFalse(codes.matches("event-1", code, T0.plusSeconds(60)), "a shared photo stops working");
    }

    @Test
    void codeForAnotherEventIsRejected() {
        assertFalse(codes.matches("event-2", codes.current("event-1", T0), T0));
    }

    @Test
    void caseAndSpacesDoNotMatter() {
        String code = codes.current("event-1", T0);
        assertTrue(codes.matches("event-1", " " + code.substring(0, 3).toLowerCase() + " " + code.substring(3) + " ", T0));
    }

    @Test
    void emptyOrMissingCodeIsRejected() {
        assertFalse(codes.matches("event-1", null, T0));
        assertFalse(codes.matches("event-1", "", T0));
    }

    @Test
    void differentSecretGivesDifferentCodes() {
        RotatingCode other = new RotatingCode("another-secret-another-secret-123456");
        assertNotEquals(codes.current("event-1", T0), other.current("event-1", T0));
    }

    @Test
    void codesLookRandomAcrossWindows() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(codes.current("event-1", T0.plusSeconds(30L * i)));
        }
        assertTrue(seen.size() >= 199, "almost no repeats over 200 windows: " + seen.size());
    }

    @Test
    void expiryIsTheEndOfTheWindow() {
        assertEquals(T0.plusSeconds(30), RotatingCode.expiresAt(T0.plusSeconds(12)));
    }

    @Test
    void shortSecretIsRefused() {
        try {
            new RotatingCode("short");
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // ok
        }
    }
}
