package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "events")
@CompoundIndex(name = "event_status_start", def = "{'status': 1, 'start': 1}")
public class Event {

    @Id
    private String id;

    /** Optimistic locking: two people acting on the same event at once cannot both win. */
    @Version
    private Long version;

    @Indexed
    private String clubId;
    private String title;
    private String description;
    private String resourceId;
    private Instant start;
    private Instant end;
    private int expectedAttendees;

    private EventStatus status = EventStatus.DRAFT;

    /** The room booking that holds the venue while the event is in review or approved. */
    private String bookingId;

    /** Increases on every submission so each submission gets its own booking idempotency key. */
    private int submissionCount;

    private String createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant submittedAt;
    private String advisorDecisionBy;
    private Instant advisorDecisionAt;
    private String managerDecisionBy;
    private Instant managerDecisionAt;
    private String rejectionReason;
    private String cancellationReason;
    /** Set once the day-before reminder has gone out, so it is sent only once. */
    private Instant reminderSentAt;

    public Event() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getClubId() { return clubId; }
    public void setClubId(String clubId) { this.clubId = clubId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Instant getStart() { return start; }
    public void setStart(Instant start) { this.start = start; }
    public Instant getEnd() { return end; }
    public void setEnd(Instant end) { this.end = end; }
    public int getExpectedAttendees() { return expectedAttendees; }
    public void setExpectedAttendees(int expectedAttendees) { this.expectedAttendees = expectedAttendees; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public int getSubmissionCount() { return submissionCount; }
    public void setSubmissionCount(int submissionCount) { this.submissionCount = submissionCount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public String getAdvisorDecisionBy() { return advisorDecisionBy; }
    public void setAdvisorDecisionBy(String advisorDecisionBy) { this.advisorDecisionBy = advisorDecisionBy; }
    public Instant getAdvisorDecisionAt() { return advisorDecisionAt; }
    public void setAdvisorDecisionAt(Instant advisorDecisionAt) { this.advisorDecisionAt = advisorDecisionAt; }
    public String getManagerDecisionBy() { return managerDecisionBy; }
    public void setManagerDecisionBy(String managerDecisionBy) { this.managerDecisionBy = managerDecisionBy; }
    public Instant getManagerDecisionAt() { return managerDecisionAt; }
    public void setManagerDecisionAt(Instant managerDecisionAt) { this.managerDecisionAt = managerDecisionAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
}
