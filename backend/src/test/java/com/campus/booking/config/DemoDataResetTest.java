package com.campus.booking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.AuditLogRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.FeedbackRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.repository.ResourceRepository;
import com.campus.booking.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The reset deletes every event, booking and registration, so this runs only against a throwaway database:
 * MONGODB_URI must name campus_ci or campus_test (CI does). It is skipped against a real or shared database.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".*/campus_(ci|test)\\b.*")
class DemoDataResetTest {

    @Autowired UserRepository users;
    @Autowired ResourceRepository resources;
    @Autowired ClubRepository clubs;
    @Autowired PasswordEncoder encoder;
    @Autowired EventRepository events;
    @Autowired BookingRepository bookings;
    @Autowired RegistrationRepository registrations;
    @Autowired AttendanceRepository attendance;
    @Autowired FeedbackRepository feedback;
    @Autowired AuditLogRepository audit;

    private DemoDataReset reset(boolean on, boolean seeded) {
        return new DemoDataReset(on, seeded, "Asia/Kolkata", events, bookings, registrations, attendance, feedback,
                audit, resources, clubs);
    }

    @Test
    void resetLeavesCleanDemoEventsAndIsOffByDefault() {
        new DemoDataSeeder(users, resources, clubs, encoder, true, "Demo-Test-1").run();

        // Leftovers from "testing" that the reset has to remove.
        Event stray = new Event();
        stray.setTitle("Created to test something");
        stray.setStatus(EventStatus.APPROVED);
        events.save(stray);

        // Off: nothing is touched.
        reset(false, true).run();
        assertTrue(events.findAll().stream().anyMatch((e) -> "Created to test something".equals(e.getTitle())));

        // On: the strays are gone and the demo set is there.
        reset(true, true).run();
        List<Event> all = events.findAll();
        assertEquals(5, all.size());
        assertFalse(all.stream().anyMatch((e) -> "Created to test something".equals(e.getTitle())));
        assertEquals(5, bookings.count());
        for (Event e : all) {
            assertNotNull(e.getBookingId(), e.getTitle());
            Booking b = bookings.findById(e.getBookingId()).orElseThrow();
            assertEquals(BookingStatus.APPROVED, b.getStatus());
            assertEquals(e.getId(), b.getEventId());
            assertEquals(e.getStart(), b.getStart());
            assertEquals(e.getEnd(), b.getEnd());
        }

        // One event is running right now, so check-in is open at any time of day.
        Instant now = Instant.now();
        assertEquals(1, all.stream().filter((e) -> e.getStatus() == EventStatus.APPROVED
                && e.getStart().isBefore(now) && e.getEnd().isAfter(now)).count());
        // One is over and completed, with two check-ins.
        List<Event> done = all.stream().filter((e) -> e.getStatus() == EventStatus.COMPLETED).toList();
        assertEquals(1, done.size());
        assertEquals(2, attendance.countByEventId(done.get(0).getId()));
        // The 2-seat workshop is full.
        Event workshop = all.stream().filter((e) -> e.getTitle().startsWith("Robotics")).findFirst().orElseThrow();
        assertEquals(2, registrations.countByEventIdAndStatus(workshop.getId(), RegistrationStatus.CONFIRMED));
        assertEquals(2, workshop.getExpectedAttendees());
        // Users, rooms and the club are kept.
        assertTrue(users.existsByEmail("admin@campus.test"));
        assertTrue(clubs.count() > 0);

        // Safe to run twice: still exactly five events.
        reset(true, true).run();
        assertEquals(5, events.count());

        // Without the demo seed switch, nothing is reset.
        reset(true, false).run();
        assertEquals(5, events.count());
    }
}
