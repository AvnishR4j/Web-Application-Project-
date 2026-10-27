package com.campus.booking.security.google;

import java.util.Optional;

/** Checks a Google ID token and returns who it belongs to, or nothing if the token is not valid. */
public interface GoogleTokenVerifier {

    /** True when a Google client ID is configured, so "Continue with Google" is offered. */
    boolean enabled();

    Optional<GoogleIdentity> verify(String idToken);
}
