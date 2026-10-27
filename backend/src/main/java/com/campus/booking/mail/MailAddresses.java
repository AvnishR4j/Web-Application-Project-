package com.campus.booking.mail;

import java.util.Locale;
import java.util.Set;

/** Addresses that can never receive mail (demo accounts on reserved domains) are skipped, to protect the sender's reputation. */
public final class MailAddresses {

    private static final Set<String> RESERVED_TLDS = Set.of("test", "example", "invalid", "localhost", "local");
    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.org", "example.net");

    private MailAddresses() {
    }

    public static boolean deliverable(String email) {
        if (email == null) {
            return false;
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        int at = e.lastIndexOf('@');
        if (at <= 0 || at == e.length() - 1 || at != e.indexOf('@')) {
            return false;
        }
        String domain = e.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        return !RESERVED_TLDS.contains(domain.substring(dot + 1)) && !RESERVED_DOMAINS.contains(domain);
    }
}
