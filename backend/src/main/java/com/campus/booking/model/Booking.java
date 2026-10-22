package com.campus.booking.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bookings")
@CompoundIndex(name = "booking_resource_time", def = "{'resourceId': 1, 'start': 1, 'end': 1}")
public class Booking {

    @Id
    private String id;
    private String resourceId;
    private String requesterEmail;

    /** Set when this booking holds the venue for an event; such bookings change only through the event. */
    private String eventId;
    private String title;
    private Instant start;
    private Instant end;
    private int attendees;
    private BookingStatus status = BookingStatus.PENDING;

    /** "<requester email>:<client key>". Unique, so a repeated request cannot create a second booking. */
    @JsonIgnore
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    private Instant createdAt = Instant.now();
    private String decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    private boolean lateCancellation;

    public Booking() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getStart() { return start; }
    public void setStart(Instant start) { this.start = start; }
    public Instant getEnd() { return end; }
    public void setEnd(Instant end) { this.end = end; }
    public int getAttendees() { return attendees; }
    public void setAttendees(int attendees) { this.attendees = attendees; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    @JsonIgnore
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public boolean isLateCancellation() { return lateCancellation; }
    public void setLateCancellation(boolean lateCancellation) { this.lateCancellation = lateCancellation; }
}
