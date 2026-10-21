package com.campus.booking.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Time during which a resource (or every resource, when resourceId is null) cannot be booked. */
@Document(collection = "blockedPeriods")
@CompoundIndex(name = "blocked_resource_time", def = "{'resourceId': 1, 'start': 1, 'end': 1}")
public class BlockedPeriod {

    @Id
    private String id;
    private String resourceId;
    private Instant start;
    private Instant end;
    private BlockedKind kind;
    private String reason;
    private String createdBy;

    public BlockedPeriod() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Instant getStart() { return start; }
    public void setStart(Instant start) { this.start = start; }
    public Instant getEnd() { return end; }
    public void setEnd(Instant end) { this.end = end; }
    public BlockedKind getKind() { return kind; }
    public void setKind(BlockedKind kind) { this.kind = kind; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
