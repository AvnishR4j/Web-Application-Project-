package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A short-lived secret sent by email: a 6-digit sign-in code or a password-reset link. Only a hash is stored,
 * and MongoDB deletes the record by itself once it expires.
 */
@Document(collection = "email_tokens")
@CompoundIndex(name = "email_purpose_created", def = "{'email': 1, 'purpose': 1, 'createdAt': -1}")
public class EmailToken {

    public enum Purpose { LOGIN_CODE, PASSWORD_RESET }

    @Id
    private String id;
    private String email;
    private Purpose purpose;
    @Indexed
    private String hash;
    private int attempts;
    private Instant createdAt;
    /** Works until this moment. */
    private Instant validUntil;
    /** MongoDB deletes the record at this moment (kept an hour so the hourly request limit can count it). */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public EmailToken() {
    }

    public EmailToken(String email, Purpose purpose, String hash, Instant createdAt, Instant validUntil) {
        this.email = email;
        this.purpose = purpose;
        this.hash = hash;
        this.createdAt = createdAt;
        this.validUntil = validUntil;
        Instant hourLater = createdAt.plusSeconds(3600);
        this.expiresAt = validUntil.isAfter(hourLater) ? validUntil : hourLater;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public Purpose getPurpose() { return purpose; }
    public String getHash() { return hash; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getValidUntil() { return validUntil; }
    public Instant getExpiresAt() { return expiresAt; }
}
