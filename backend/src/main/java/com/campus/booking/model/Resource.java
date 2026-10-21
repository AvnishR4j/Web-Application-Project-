package com.campus.booking.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "resources")
public class Resource {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private ResourceType type;
    private String location;

    /** Seats for rooms and labs. Ignored for equipment. */
    private int capacity;

    /** Setup and teardown gap required between two bookings of this resource. */
    private int bufferMinutes;

    /** Daily availability as "HH:mm" in the campus time zone. Both null means available all day. */
    private String openTime;
    private String closeTime;

    /** Labs and equipment can require a training or sign-off record before booking. */
    private boolean requiresTraining;

    private boolean active = true;

    /**
     * Bumped inside every booking transaction. Two transactions that touch the same document
     * collide (MongoDB write conflict), which is what stops two concurrent requests from both
     * seeing a free slot. Never exposed through the API.
     */
    @JsonIgnore
    private long lockVersion;

    public Resource() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getBufferMinutes() { return bufferMinutes; }
    public void setBufferMinutes(int bufferMinutes) { this.bufferMinutes = bufferMinutes; }
    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }
    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
    public boolean isRequiresTraining() { return requiresTraining; }
    public void setRequiresTraining(boolean requiresTraining) { this.requiresTraining = requiresTraining; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    @JsonIgnore
    public long getLockVersion() { return lockVersion; }
    public void setLockVersion(long lockVersion) { this.lockVersion = lockVersion; }
}
