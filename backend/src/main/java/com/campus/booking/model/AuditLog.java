package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** One line per change to an event: who did what, when, and why. Never updated or deleted by the app. */
@Document(collection = "auditLogs")
public class AuditLog {

    @Id
    private String id;
    @Indexed
    private String eventId;
    private String action;
    private EventStatus fromStatus;
    private EventStatus toStatus;
    private String actorEmail;
    private String reason;
    private Instant at = Instant.now();

    public AuditLog() {
    }

    public AuditLog(String eventId, String action, EventStatus from, EventStatus to, String actorEmail, String reason) {
        this.eventId = eventId;
        this.action = action;
        this.fromStatus = from;
        this.toStatus = to;
        this.actorEmail = actorEmail;
        this.reason = reason;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public EventStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(EventStatus fromStatus) { this.fromStatus = fromStatus; }
    public EventStatus getToStatus() { return toStatus; }
    public void setToStatus(EventStatus toStatus) { this.toStatus = toStatus; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
