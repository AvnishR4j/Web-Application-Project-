package com.campus.booking.mail;

import com.campus.booking.model.EventStatus;

/** Things that happened which someone should hear about. Published by the services, emailed after the change is saved. */
public final class MailEvents {

    private MailEvents() {
    }

    public enum Seat { CONFIRMED, WAITLISTED, PROMOTED }

    public record RegistrationChanged(String eventId, String email, Seat seat) {
    }

    public record EventStatusChanged(String eventId, EventStatus from, EventStatus to, String reason) {
    }

    public record BookingDecided(String bookingId) {
    }
}
