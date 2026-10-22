package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "attendance")
@CompoundIndex(name = "attendance_event_user", def = "{'eventId': 1, 'userEmail': 1}", unique = true)
public class Attendance {

    @Id
    private String id;
    private String eventId;
    private String userEmail;
    private Instant checkedInAt = Instant.now();

    public Attendance() {
    }

    public Attendance(String eventId, String userEmail, Instant at) {
        this.eventId = eventId;
        this.userEmail = userEmail;
        this.checkedInAt = at;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
}
