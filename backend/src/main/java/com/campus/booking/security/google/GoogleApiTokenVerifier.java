package com.campus.booking.security.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the token's signature against Google's public keys, and that it was issued to this app's client ID,
 * by Google, and has not expired. Google's keys are fetched over HTTPS and cached by the library.
 */
@Component
public class GoogleApiTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiTokenVerifier.class);

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleApiTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = this.clientId.isEmpty() ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(this.clientId)).build();
    }

    @Override
    public boolean enabled() {
        return verifier != null;
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        if (verifier == null || idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload p = token.getPayload();
            Object name = p.get("name");
            return Optional.of(new GoogleIdentity(p.getEmail(), name == null ? null : name.toString(),
                    Boolean.TRUE.equals(p.getEmailVerified()), p.getHostedDomain()));
        } catch (Exception e) {
            log.warn("Google token could not be verified: {}", e.toString());
            return Optional.empty();
        }
    }
}
