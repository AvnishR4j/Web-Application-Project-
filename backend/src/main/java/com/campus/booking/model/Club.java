package com.campus.booking.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A student club: its faculty advisor approves its events, its organizers create them. */
@Document(collection = "clubs")
public class Club {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;

    @Indexed
    private String advisorEmail;

    @Indexed
    private List<String> organizerEmails = new ArrayList<>();

    private Instant createdAt = Instant.now();

    public Club() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAdvisorEmail() { return advisorEmail; }
    public void setAdvisorEmail(String advisorEmail) { this.advisorEmail = advisorEmail; }
    public List<String> getOrganizerEmails() { return organizerEmails; }
    public void setOrganizerEmails(List<String> organizerEmails) { this.organizerEmails = organizerEmails; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isOrganizer(String email) {
        return organizerEmails != null && organizerEmails.contains(email);
    }
}
