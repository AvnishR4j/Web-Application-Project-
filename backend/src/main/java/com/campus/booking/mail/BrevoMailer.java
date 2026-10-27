package com.campus.booking.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends through Brevo's HTTPS API (free tier: 300 emails a day). HTTPS rather than SMTP because free hosting
 * such as Render blocks outgoing mail ports. With no API key set, emails are only logged (subject and count,
 * never the contents, which can hold sign-in codes).
 */
@Component
public class BrevoMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(BrevoMailer.class);
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");
    /** Brevo accepts up to 1000 versions per call; stay well inside it. */
    private static final int BATCH = 500;

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public BrevoMailer(@Value("${app.mail.brevo-api-key:}") String apiKey,
                       @Value("${app.mail.from:}") String fromEmail,
                       @Value("${app.mail.from-name:Campus Board}") String fromName,
                       ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName;
        this.json = json;
    }

    @Override
    public boolean enabled() {
        return !apiKey.isEmpty() && !fromEmail.isEmpty();
    }

    @Override
    @Async
    public void send(List<Message> messages) {
        List<Message> real = messages.stream().filter((m) -> MailAddresses.deliverable(m.to())).toList();
        if (real.isEmpty()) {
            return;
        }
        if (!enabled()) {
            log.info("Email is not configured; skipped \"{}\" to {} recipient(s)", real.get(0).subject(), real.size());
            return;
        }
        for (int i = 0; i < real.size(); i += BATCH) {
            post(real.subList(i, Math.min(real.size(), i + BATCH)));
        }
    }

    private void post(List<Message> batch) {
        List<Map<String, Object>> versions = new ArrayList<>();
        for (Message m : batch) {
            versions.add(Map.of("to", List.of(Map.of("email", m.to())), "subject", m.subject(), "htmlContent", m.html()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("email", fromEmail, "name", fromName));
        body.put("subject", batch.get(0).subject());
        body.put("htmlContent", batch.get(0).html());
        body.put("messageVersions", versions);
        try {
            HttpRequest req = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", apiKey)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("Brevo refused \"{}\" ({} recipient(s)): HTTP {} {}", batch.get(0).subject(), batch.size(),
                        res.statusCode(), res.body());
            } else {
                log.info("Sent \"{}\" to {} recipient(s)", batch.get(0).subject(), batch.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Could not send \"{}\": {}", batch.get(0).subject(), e.toString());
        }
    }
}
