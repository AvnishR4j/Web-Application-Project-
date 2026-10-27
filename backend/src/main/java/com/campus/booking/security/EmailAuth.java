package com.campus.booking.security;

import static com.campus.booking.mail.MailTemplates.para;

import com.campus.booking.dto.AuthDtos.AuthResponse;
import com.campus.booking.dto.AuthDtos.UserResponse;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.UnauthorizedException;
import com.campus.booking.mail.MailAddresses;
import com.campus.booking.mail.MailTemplates;
import com.campus.booking.mail.Mailer;
import com.campus.booking.mail.Mailer.Message;
import com.campus.booking.model.EmailToken;
import com.campus.booking.model.EmailToken.Purpose;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.EmailTokenRepository;
import com.campus.booking.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Sign-in with a 6-digit code sent to a college email, and password reset by email link.
 *
 * Safety rules: only hashes are stored; a code lasts 10 minutes and allows 5 tries; at most one new code a
 * minute and 5 an hour per address; a reset link lasts 30 minutes and works once. Requests never reveal whether
 * an account exists.
 */
@Service
public class EmailAuth {

    static final Duration CODE_LIFE = Duration.ofMinutes(10);
    static final Duration RESET_LIFE = Duration.ofMinutes(30);
    static final int MAX_TRIES = 5;
    static final Duration RESEND_GAP = Duration.ofSeconds(60);
    static final int MAX_PER_HOUR = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final EmailTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Mailer mailer;
    private final MailTemplates t;
    private final Clock clock;
    private final String pepper;
    private final List<String> collegeDomains;

    public EmailAuth(UserRepository users, EmailTokenRepository tokens, PasswordEncoder encoder, JwtService jwt,
                     Mailer mailer, MailTemplates t, Clock clock, @Value("${app.jwt.secret}") String secret,
                     @Value("${app.google.allowed-domains:thapar.edu}") String domains) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mailer = mailer;
        this.t = t;
        this.clock = clock;
        this.pepper = secret;
        this.collegeDomains = Arrays.stream(domains.split(","))
                .map((d) -> d.trim().toLowerCase(Locale.ROOT)).filter((d) -> !d.isEmpty()).toList();
    }

    public boolean enabled() {
        return mailer.enabled();
    }

    public List<String> collegeDomains() {
        return collegeDomains;
    }

    // ----- sign-in code -----

    /** Sends a code if the address is on a college domain and not asking too often. Always looks the same to the caller. */
    public void requestCode(String rawEmail) {
        String email = normalise(rawEmail);
        if (!onCollegeDomain(email)) {
            throw new ForbiddenException("Use your college email (@" + String.join(", @", collegeDomains) + ").");
        }
        if (!mayIssue(email, Purpose.LOGIN_CODE)) {
            return;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now(clock);
        tokens.save(new EmailToken(email, Purpose.LOGIN_CODE, hash(Purpose.LOGIN_CODE, email, code), now, now.plus(CODE_LIFE)));
        String h = "Your Campus Board sign-in code: " + code;
        mailer.send(new Message(email, h, t.page("Your sign-in code",
                para("Enter this code on the sign-in page. It works for 10 minutes.") + MailTemplates.code(code)
                        + para("If you didn't ask for it, you can ignore this email; nobody can sign in without the code."),
                null, null, "Never share this code. Campus Board staff will never ask you for it.", null)));
    }

    public AuthResponse verifyCode(String rawEmail, String rawCode) {
        String email = normalise(rawEmail);
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        EmailToken token = tokens.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, Purpose.LOGIN_CODE)
                .filter((tk) -> tk.getValidUntil().isAfter(Instant.now(clock)))
                .orElseThrow(() -> new UnauthorizedException("That code has expired. Ask for a new one."));
        if (token.getAttempts() >= MAX_TRIES) {
            throw new UnauthorizedException("Too many wrong tries. Ask for a new code.");
        }
        // Only the newest code counts (findFirst... newest), so an older email's code no longer works.
        if (!code.matches("\\d{6}") || !same(token.getHash(), hash(Purpose.LOGIN_CODE, email, code))) {
            token.setAttempts(token.getAttempts() + 1);
            tokens.save(token);
            int left = MAX_TRIES - token.getAttempts();
            throw new UnauthorizedException(left > 0 ? "That code isn't right. " + left + (left == 1 ? " try" : " tries") + " left."
                    : "Too many wrong tries. Ask for a new code.");
        }
        tokens.deleteByEmailAndPurpose(email, Purpose.LOGIN_CODE);
        User user = users.findByEmail(email).orElseGet(() -> users.save(
                new User(email.substring(0, email.indexOf('@')), email, encoder.encode(randomSecret()), Role.STUDENT, "")));
        return new AuthResponse(jwt.generate(user), UserResponse.from(user));
    }

    // ----- password reset -----

    public void requestReset(String rawEmail) {
        String email = normalise(rawEmail);
        Optional<User> user = users.findByEmail(email);
        if (user.isEmpty() || !MailAddresses.deliverable(email) || !mayIssue(email, Purpose.PASSWORD_RESET)) {
            return;
        }
        String secret = randomSecret();
        Instant now = Instant.now(clock);
        tokens.save(new EmailToken(email, Purpose.PASSWORD_RESET, hash(Purpose.PASSWORD_RESET, "", secret), now, now.plus(RESET_LIFE)));
        String h = "Reset your Campus Board password";
        mailer.send(new Message(email, h, t.page(h,
                para("Someone asked to reset the password for " + email + ". If it was you, choose a new one below. "
                        + "The link works once, for 30 minutes."),
                "Choose a new password", t.link("/reset-password?token=" + secret),
                "Didn't ask for this? Ignore this email and your password stays the same.", null)));
    }

    public AuthResponse resetPassword(String secret, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("Use at least 8 characters");
        }
        EmailToken token = tokens.findByHashAndPurpose(hash(Purpose.PASSWORD_RESET, "", secret == null ? "" : secret), Purpose.PASSWORD_RESET)
                .filter((tk) -> tk.getValidUntil().isAfter(Instant.now(clock)))
                .orElseThrow(() -> new UnauthorizedException("This reset link has expired or was already used. Ask for a new one."));
        // Only the newest link works: asking twice makes the first email's link useless.
        boolean newest = tokens.findFirstByEmailAndPurposeOrderByCreatedAtDesc(token.getEmail(), Purpose.PASSWORD_RESET)
                .map((latest) -> latest.getId().equals(token.getId())).orElse(false);
        User user = users.findByEmail(token.getEmail()).filter((u) -> newest)
                .orElseThrow(() -> new UnauthorizedException("This reset link is no longer valid. Use the newest email, or ask again."));
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        tokens.deleteByEmailAndPurpose(token.getEmail(), Purpose.PASSWORD_RESET);
        return new AuthResponse(jwt.generate(user), UserResponse.from(user));
    }

    // ----- helpers -----

    /** Exact domain match: "x@thapar.edu" yes; "x@evil-thapar.edu", "x@thapar.edu.evil.com" no. */
    boolean onCollegeDomain(String email) {
        int at = email.lastIndexOf('@');
        return at > 0 && at == email.indexOf('@') && collegeDomains.contains(email.substring(at + 1));
    }

    private boolean mayIssue(String email, Purpose purpose) {
        Instant now = Instant.now(clock);
        Optional<EmailToken> last = tokens.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minus(RESEND_GAP))) {
            return false;
        }
        return tokens.countByEmailAndPurposeAndCreatedAtAfter(email, purpose, now.minus(Duration.ofHours(1))) < MAX_PER_HOUR;
    }

    private static String normalise(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Enter your email");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(Purpose purpose, String email, String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] out = sha.digest((purpose + "|" + email + "|" + secret + "|" + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
