package com.campus.booking.attendance;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Six-character check-in codes that change every 30 seconds, like an authenticator app. Nothing is stored:
 * the code is an HMAC of the event id and the current 30-second window, so only the server can make one,
 * and a photo of the code shared later stops working. The code just before the current one is also
 * accepted, so a student who scans at the very end of a window is not turned away.
 */
public final class RotatingCode {

    public static final int WINDOW_SECONDS = 30;
    /** No 0/O, 1/I/L: easy to read aloud or type from a projector. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 6;

    private final SecretKeySpec key;

    public RotatingCode(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("The check-in secret must be at least 32 characters");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public static long windowOf(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), WINDOW_SECONDS);
    }

    /** When the code for {@code now} stops being shown (it is still accepted for one more window). */
    public static Instant expiresAt(Instant now) {
        return Instant.ofEpochSecond((windowOf(now) + 1) * WINDOW_SECONDS);
    }

    public String codeFor(String eventId, long window) {
        byte[] mac = hmac(eventId + ":" + window);
        StringBuilder out = new StringBuilder(LENGTH);
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | (mac[i] & 0xff);
        }
        for (int i = 0; i < LENGTH; i++) {
            out.append(ALPHABET[(int) Long.remainderUnsigned(bits, ALPHABET.length)]);
            bits = Long.divideUnsigned(bits, ALPHABET.length);
        }
        return out.toString();
    }

    public String current(String eventId, Instant now) {
        return codeFor(eventId, windowOf(now));
    }

    /** Accepts the current code and the one before it. Case and spaces do not matter. */
    public boolean matches(String eventId, String code, Instant now) {
        if (code == null) {
            return false;
        }
        String given = code.replaceAll("\\s", "").toUpperCase();
        long window = windowOf(now);
        return constantTimeEquals(given, codeFor(eventId, window))
                || constantTimeEquals(given, codeFor(eventId, window - 1));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
