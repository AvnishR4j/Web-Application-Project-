package com.campus.booking.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MailUnitTest {

    @Test
    void skipsAddressesThatCanNeverReceiveMail() {
        assertTrue(MailAddresses.deliverable("asha@thapar.edu"));
        assertTrue(MailAddresses.deliverable("Someone@Gmail.com"));
        assertFalse(MailAddresses.deliverable("student@campus.test"));
        assertFalse(MailAddresses.deliverable("a@example.com"));
        assertFalse(MailAddresses.deliverable("nobody"));
        assertFalse(MailAddresses.deliverable("a@b@thapar.edu"));
        assertFalse(MailAddresses.deliverable(null));
    }

    @Test
    void unsubscribeLinksCannotBeForgedForSomeoneElse() {
        UnsubscribeTokens tokens = new UnsubscribeTokens("test-secret-test-secret-test-secret-test");
        String mine = tokens.sign("asha@thapar.edu");
        assertTrue(tokens.valid("asha@thapar.edu", mine));
        assertTrue(tokens.valid("ASHA@thapar.edu", mine));
        assertFalse(tokens.valid("ravi@thapar.edu", mine));
        assertFalse(tokens.valid("asha@thapar.edu", mine + "x"));
        assertFalse(new UnsubscribeTokens("another-secret-another-secret-another").valid("asha@thapar.edu", mine));
    }

    @Test
    void escapesWhatPeopleTypeAndFormatsCampusTime() {
        MailTemplates t = new MailTemplates("https://example.app/", "Asia/Kolkata");
        String html = t.page("<b>Hi</b>", MailTemplates.para("Tom & \"Jerry\" <script>"), "Go", t.link("/events"), null, null);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;b&gt;Hi&lt;/b&gt;"));
        assertTrue(html.contains("Tom &amp; &quot;Jerry&quot;"));
        assertTrue(html.contains("https://example.app/events"));
        // 10:30 UTC is 4:00 PM in India.
        assertEquals("Fri 30 Oct, 4:00 PM – 6:00 PM",
                t.when(Instant.parse("2026-10-30T10:30:00Z"), Instant.parse("2026-10-30T12:30:00Z")));
    }
}
