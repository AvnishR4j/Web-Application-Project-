package com.campus.booking.mail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signed one-click unsubscribe links, so anyone can opt out without logging in but nobody can opt out someone else. */
@Component
public class UnsubscribeTokens {

    private final byte[] key;

    public UnsubscribeTokens(@Value("${app.jwt.secret}") String secret) {
        this.key = ("unsubscribe:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] sig = mac.doFinal(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean valid(String email, String token) {
        if (email == null || token == null) {
            return false;
        }
        return MessageDigest.isEqual(sign(email).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
