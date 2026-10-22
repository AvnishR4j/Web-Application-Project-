package com.campus.booking.booking;

import java.util.List;

/** Thrown when a booking breaks one or more conflict rules. Becomes HTTP 409 with one entry per rule. */
public class BookingConflictException extends RuntimeException {

    private final transient List<ConflictEngine.Violation> violations;

    public BookingConflictException(List<ConflictEngine.Violation> violations) {
        super("Booking conflicts with existing rules");
        this.violations = violations;
    }

    public List<ConflictEngine.Violation> getViolations() {
        return violations;
    }
}
