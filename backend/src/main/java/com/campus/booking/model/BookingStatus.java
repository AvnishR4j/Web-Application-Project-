package com.campus.booking.model;

import java.util.List;

public enum BookingStatus {
    PENDING, APPROVED, REJECTED, CANCELLED;

    /** Statuses that hold a slot. A pending request already blocks others so two clubs cannot both wait on one slot. */
    public static final List<BookingStatus> ACTIVE = List.of(PENDING, APPROVED);

    public boolean holdsSlot() {
        return this == PENDING || this == APPROVED;
    }
}
