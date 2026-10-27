package com.campus.booking.security.google;

import com.campus.booking.dto.AuthDtos.AuthResponse;
import com.campus.booking.dto.AuthDtos.UserResponse;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.UnauthorizedException;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.UserRepository;
import com.campus.booking.security.JwtService;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * "Continue with Google", limited to the college's email domain(s).
 *
 * A token is accepted only when Google has verified it, the email is verified, and the email's domain is on the
 * allowed list (the hosted-domain claim, when Google sends one, must agree). Anyone new becomes a STUDENT, always:
 * roles are only ever changed by a super admin. Someone who already has an account with that email signs in to it
 * and keeps their role.
 */
@Service
public class GoogleSignIn {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final GoogleTokenVerifier verifier;
    private final List<String> allowedDomains;

    public GoogleSignIn(UserRepository users, PasswordEncoder encoder, JwtService jwt, GoogleTokenVerifier verifier,
                        @Value("${app.google.allowed-domains:thapar.edu}") String allowedDomains) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.verifier = verifier;
        this.allowedDomains = Arrays.stream(allowedDomains.split(","))
                .map((d) -> d.trim().toLowerCase(Locale.ROOT)).filter((d) -> !d.isEmpty()).toList();
    }

    public boolean enabled() {
        return verifier.enabled();
    }

    public List<String> allowedDomains() {
        return allowedDomains;
    }

    /** The rule on its own, so it can be tested without Google. The domain must match exactly: no suffix tricks. */
    public static boolean admits(GoogleIdentity who, List<String> allowedDomains) {
        if (who == null || who.email() == null || !who.emailVerified()) {
            return false;
        }
        String email = who.email().trim().toLowerCase(Locale.ROOT);
        int at = email.lastIndexOf('@');
        if (at <= 0 || at != email.indexOf('@')) {
            return false;
        }
        String domain = email.substring(at + 1);
        if (!allowedDomains.contains(domain)) {
            return false;
        }
        String hosted = who.hostedDomain();
        return hosted == null || hosted.isBlank() || hosted.trim().equalsIgnoreCase(domain);
    }

    public AuthResponse signIn(String idToken) {
        Optional<GoogleIdentity> who = verifier.verify(idToken);
        if (who.isEmpty()) {
            throw new UnauthorizedException("Google sign-in failed. Please try again.");
        }
        if (!admits(who.get(), allowedDomains)) {
            throw new ForbiddenException("Please use your college Google account (@"
                    + String.join(", @", allowedDomains) + ").");
        }
        String email = who.get().email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email).orElseGet(() -> users.save(new User(displayName(who.get(), email), email,
                encoder.encode(unguessable()), Role.STUDENT, "")));
        return new AuthResponse(jwt.generate(user), UserResponse.from(user));
    }

    private static String displayName(GoogleIdentity who, String email) {
        String name = who.name() == null ? "" : who.name().trim();
        String shown = name.isEmpty() ? email.substring(0, email.indexOf('@')) : name;
        return shown.length() > 100 ? shown.substring(0, 100) : shown;
    }

    /** People who sign in with Google have no password; this one is random and never shown to anyone. */
    private static String unguessable() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
