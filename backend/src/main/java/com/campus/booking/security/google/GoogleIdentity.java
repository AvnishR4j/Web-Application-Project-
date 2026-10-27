package com.campus.booking.security.google;

/** What Google vouches for in a verified ID token. */
public record GoogleIdentity(String email, String name, boolean emailVerified, String hostedDomain) {
}
