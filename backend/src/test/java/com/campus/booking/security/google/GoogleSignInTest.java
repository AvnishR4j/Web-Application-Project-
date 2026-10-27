package com.campus.booking.security.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.UnauthorizedException;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.UserRepository;
import com.campus.booking.security.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GoogleSignInTest {

    private static final List<String> DOMAINS = List.of("thapar.edu");

    private static GoogleIdentity who(String email, boolean verified, String hd) {
        return new GoogleIdentity(email, "Asha Rao", verified, hd);
    }

    @Test
    void admitsVerifiedCollegeEmail() {
        assertTrue(GoogleSignIn.admits(who("asha@thapar.edu", true, "thapar.edu"), DOMAINS));
        assertTrue(GoogleSignIn.admits(who("Asha@Thapar.EDU", true, null), DOMAINS));
    }

    @Test
    void rejectsOtherDomainsAndLookalikes() {
        assertFalse(GoogleSignIn.admits(who("asha@gmail.com", true, null), DOMAINS));
        assertFalse(GoogleSignIn.admits(who("asha@evil-thapar.edu", true, null), DOMAINS));
        assertFalse(GoogleSignIn.admits(who("asha@thapar.edu.evil.com", true, null), DOMAINS));
        assertFalse(GoogleSignIn.admits(who("asha@mail.thapar.edu", true, null), DOMAINS));
        assertFalse(GoogleSignIn.admits(who("x@evil.com@thapar.edu", true, null), DOMAINS));
    }

    @Test
    void rejectsUnverifiedEmailAndMismatchedHostedDomain() {
        assertFalse(GoogleSignIn.admits(who("asha@thapar.edu", false, "thapar.edu"), DOMAINS));
        assertFalse(GoogleSignIn.admits(who("asha@thapar.edu", true, "other.edu"), DOMAINS));
        assertFalse(GoogleSignIn.admits(null, DOMAINS));
    }

    private record Fixture(GoogleSignIn service, UserRepository users, GoogleTokenVerifier verifier) {
    }

    private Fixture fixture() {
        UserRepository users = mock(UserRepository.class);
        GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
        when(users.save(any(User.class))).thenAnswer((i) -> i.getArgument(0));
        JwtService jwt = new JwtService("test-secret-test-secret-test-secret-test-secret-123", 60L);
        return new Fixture(new GoogleSignIn(users, new BCryptPasswordEncoder(), jwt, verifier, "thapar.edu"),
                users, verifier);
    }

    @Test
    void newCollegeUserBecomesStudent() {
        Fixture f = fixture();
        when(f.verifier().verify("tok")).thenReturn(Optional.of(who("asha@thapar.edu", true, "thapar.edu")));
        when(f.users().findByEmail("asha@thapar.edu")).thenReturn(Optional.empty());

        var res = f.service().signIn("tok");

        assertEquals(Role.STUDENT, res.user().role());
        assertEquals("asha@thapar.edu", res.user().email());
        assertFalse(res.token().isBlank());
    }

    @Test
    void existingUserKeepsTheirRole() {
        Fixture f = fixture();
        User organizer = new User("Org", "org@thapar.edu", "hash", Role.CLUB_ORGANIZER, "CSE");
        when(f.verifier().verify("tok")).thenReturn(Optional.of(who("org@thapar.edu", true, null)));
        when(f.users().findByEmail("org@thapar.edu")).thenReturn(Optional.of(organizer));

        var res = f.service().signIn("tok");

        assertEquals(Role.CLUB_ORGANIZER, res.user().role());
        verify(f.users(), never()).save(any(User.class));
    }

    @Test
    void outsiderIsRefusedAndBadTokenIsUnauthorized() {
        Fixture f = fixture();
        when(f.verifier().verify("good")).thenReturn(Optional.of(who("a@gmail.com", true, null)));
        when(f.verifier().verify("bad")).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> f.service().signIn("good"));
        assertThrows(UnauthorizedException.class, () -> f.service().signIn("bad"));
        verify(f.users(), never()).save(any(User.class));
    }
}
