package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Proof that a user has completed training or sign-off for a lab or piece of equipment. */
@Document(collection = "trainingRecords")
@CompoundIndex(name = "training_user_resource", def = "{'userEmail': 1, 'resourceId': 1}", unique = true)
public class TrainingRecord {

    @Id
    private String id;
    private String userEmail;
    private String resourceId;
    private String grantedBy;
    private Instant grantedAt = Instant.now();

    public TrainingRecord() {
    }

    public TrainingRecord(String userEmail, String resourceId, String grantedBy) {
        this.userEmail = userEmail;
        this.resourceId = resourceId;
        this.grantedBy = grantedBy;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
