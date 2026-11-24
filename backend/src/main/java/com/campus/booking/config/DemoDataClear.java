package com.campus.booking.config;

import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.AuditLogRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.FeedbackRepository;
import com.campus.booking.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Empties everything that people create (events and their approvals, bookings, registrations, check-ins,
 * feedback and the audit log) and adds nothing back. Users, rooms, clubs and training records are kept.
 * Runs at start-up only when CLEAR_DEMO_DATA=true: switch it on, restart once, then switch it off again.
 */
@Component
@Order(3)
public class DemoDataClear implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataClear.class);

    private final boolean clear;
    private final EventRepository events;
    private final BookingRepository bookings;
    private final RegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final FeedbackRepository feedback;
    private final AuditLogRepository audit;

    public DemoDataClear(@Value("${app.clear-demo-data:false}") boolean clear, EventRepository events,
                         BookingRepository bookings, RegistrationRepository registrations,
                         AttendanceRepository attendance, FeedbackRepository feedback, AuditLogRepository audit) {
        this.clear = clear;
        this.events = events;
        this.bookings = bookings;
        this.registrations = registrations;
        this.attendance = attendance;
        this.feedback = feedback;
        this.audit = audit;
    }

    @Override
    public void run(String... args) {
        if (!clear) {
            return;
        }
        attendance.deleteAll();
        feedback.deleteAll();
        registrations.deleteAll();
        events.deleteAll();
        bookings.deleteAll();
        audit.deleteAll();
        log.warn("CLEAR_DEMO_DATA: all events, bookings, registrations, check-ins, feedback and audit entries were deleted. "
                + "Set CLEAR_DEMO_DATA back to false so it does not run again.");
    }
}
