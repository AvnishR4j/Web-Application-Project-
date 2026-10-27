package com.campus.booking.mail;

import java.util.List;

/** Sends email. Calls return at once; delivery happens in the background and failures are only logged. */
public interface Mailer {

    record Message(String to, String subject, String html) {
    }

    /** True when an email provider is configured, so features that depend on email can be offered. */
    boolean enabled();

    void send(List<Message> messages);

    default void send(Message message) {
        send(List.of(message));
    }
}
