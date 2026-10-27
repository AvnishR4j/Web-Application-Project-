package com.campus.booking.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One simple, branded layout for every email. Everything user-supplied is escaped. */
@Component
public class MailTemplates {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final String baseUrl;
    private final ZoneId zone;

    public MailTemplates(@Value("${app.base-url:https://campus-board-sepia-five.vercel.app}") String baseUrl,
                         @Value("${app.booking.zone}") String zone) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.zone = ZoneId.of(zone);
    }

    public String link(String path) {
        return baseUrl + path;
    }

    /** "Fri 30 Oct, 4:00 PM – 6:00 PM" in the campus time zone. */
    public String when(Instant start, Instant end) {
        var s = start.atZone(zone);
        var e = end.atZone(zone);
        String day = DAY.format(s);
        String range = TIME.format(s) + " – " + TIME.format(e);
        return s.toLocalDate().equals(e.toLocalDate()) ? day + ", " + range : day + " " + TIME.format(s) + " – " + DAY.format(e) + " " + TIME.format(e);
    }

    /** A detail row such as "When" / "Fri 30 Oct, 4:00 PM". */
    public static String row(String label, String value) {
        return "<tr><td style=\"padding:6px 0;color:#6b7178;font-size:14px;width:84px;vertical-align:top\">" + esc(label)
                + "</td><td style=\"padding:6px 0;color:#1f2226;font-size:14px;font-weight:600\">" + esc(value) + "</td></tr>";
    }

    public static String details(String... rows) {
        return "<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;margin:18px 0 6px;"
                + "border-top:1px solid #e6e3dc;border-bottom:1px solid #e6e3dc\">" + String.join("", rows) + "</table>";
    }

    public static String para(String text) {
        return "<p style=\"margin:0 0 14px;color:#3a3f45;font-size:15px;line-height:1.6\">" + esc(text) + "</p>";
    }

    /** A big code for sign-in emails. */
    public static String code(String code) {
        return "<div style=\"margin:20px 0;padding:18px;text-align:center;background:#f3f1ec;border-radius:14px;"
                + "font-size:34px;letter-spacing:10px;font-weight:700;color:#1f2226;font-family:Menlo,Consolas,monospace\">"
                + esc(code) + "</div>";
    }

    /**
     * The full email. {@code body} is already-built HTML from the helpers above. {@code footerNote} is plain text,
     * {@code unsubscribeUrl} may be null.
     */
    public String page(String heading, String body, String buttonText, String buttonUrl, String footerNote, String unsubscribeUrl) {
        String button = buttonText == null ? "" :
                "<p style=\"margin:22px 0 4px\"><a href=\"" + esc(buttonUrl) + "\" style=\"display:inline-block;background:#1f2226;"
                        + "color:#ece8de;text-decoration:none;font-weight:700;font-size:15px;padding:13px 22px;border-radius:12px\">"
                        + esc(buttonText) + "</a></p>";
        String unsub = unsubscribeUrl == null ? "" :
                " · <a href=\"" + esc(unsubscribeUrl) + "\" style=\"color:#8a8f95\">Stop new-event emails</a>";
        return "<!doctype html><html><body style=\"margin:0;padding:0;background:#f3f1ec\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"background:#f3f1ec;padding:28px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:520px;font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif\">"
                + "<tr><td style=\"padding:0 6px 14px;font-weight:700;font-size:15px;color:#1f2226\">"
                + "<span style=\"display:inline-block;width:10px;height:10px;border-radius:50%;background:#d9c296;margin-right:8px\"></span>"
                + "Campus Board</td></tr>"
                + "<tr><td style=\"background:#ffffff;border-radius:18px;padding:28px 26px;border:1px solid #e6e3dc\">"
                + "<h1 style=\"margin:0 0 14px;font-size:22px;line-height:1.3;color:#1f2226;font-weight:600\">" + esc(heading) + "</h1>"
                + body + button + "</td></tr>"
                + "<tr><td style=\"padding:16px 6px 0;color:#8a8f95;font-size:12px;line-height:1.6\">"
                + esc(footerNote == null ? "You get this because you use Campus Board at your college." : footerNote)
                + unsub + "</td></tr></table></td></tr></table></body></html>";
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
