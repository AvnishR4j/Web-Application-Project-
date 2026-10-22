package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** One rating per attendee per event. Shown to organizers without the author's email. */
@Document(collection = "feedback")
@CompoundIndex(name = "feedback_event_user", def = "{'eventId': 1, 'userEmail': 1}", unique = true)
public class Feedback {

    @Id
    private String id;
    private String eventId;
    private String userEmail;
    private int rating;
    private String comment;
    private Instant createdAt = Instant.now();

    public Feedback() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
