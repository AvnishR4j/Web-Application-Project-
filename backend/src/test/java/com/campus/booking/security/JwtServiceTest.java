package com.campus.booking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final JwtService service = new JwtService(SECRET, 60);
    private final User user = new User("Test", "test@campus.test", "hash", Role.CLUB_ORGANIZER, "CS");

    @Test
    void roundTripKeepsEmailAndRole() {
        var identity = service.parse(service.generate(user)).orElseThrow();
        assertEquals("test@campus.test", identity.email());
        assertEquals(Role.CLUB_ORGANIZER, identity.role());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.generate(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertTrue(service.parse(tampered).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService("another-secret-another-secret-123456", 60);
        assertTrue(service.parse(other.generate(user)).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expired = new JwtService(SECRET, -1);
        assertTrue(service.parse(expired.generate(user)).isEmpty());
    }

    @Test
    void garbageIsRejected() {
        assertTrue(service.parse("not-a-jwt").isEmpty());
    }

    @Test
    void shortSecretIsRefused() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60));
    }
}
