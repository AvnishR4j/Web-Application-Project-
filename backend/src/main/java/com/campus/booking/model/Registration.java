package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/** One student's place at one event. Re-registering after cancelling reuses the same record. */
@Document(collection = "registrations")
@CompoundIndexes({
    @CompoundIndex(name = "registration_event_user", def = "{'eventId': 1, 'userEmail': 1}", unique = true),
    @CompoundIndex(name = "registration_event_status_queue", def = "{'eventId': 1, 'status': 1, 'waitlistedAt': 1}")
})
public class Registration {

    @Id
    private String id;
    private String eventId;
    private String userEmail;
    private RegistrationStatus status;
    /** True when this seat came from the waitlist after someone else cancelled. */
    private boolean promoted;
    private Instant createdAt = Instant.now();
    private Instant confirmedAt;
    /** Queue position is by this time: earliest first. */
    private Instant waitlistedAt;
    private Instant cancelledAt;

    public Registration() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }
    public boolean isPromoted() { return promoted; }
    public void setPromoted(boolean promoted) { this.promoted = promoted; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getWaitlistedAt() { return waitlistedAt; }
    public void setWaitlistedAt(Instant waitlistedAt) { this.waitlistedAt = waitlistedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
