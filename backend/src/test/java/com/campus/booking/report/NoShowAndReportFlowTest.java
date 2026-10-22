package com.campus.booking.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.booking.Requester;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.model.Attendance;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.model.ResourceType;
import com.campus.booking.model.Role;
import com.campus.booking.registration.RegistrationService;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.repository.ResourceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Phase 6: the no-show rule and the reports, against a real database. Runs only when MONGODB_URI is set. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class NoShowAndReportFlowTest {

    @Autowired RegistrationService registrationService;
    @Autowired ReportService reports;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired AttendanceRepository attendance;
    @Autowired ResourceRepository resources;
    @Autowired BookingRepository bookings;

    private final List<String> eventIds = new ArrayList<>();
    private final List<String> resourceIds = new ArrayList<>();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        for (String id : eventIds) {
            attendance.deleteByEventId(id);
            registrations.deleteByEventId(id);
            events.deleteById(id);
        }
        for (String id : resourceIds) {
            bookings.deleteByResourceId(id);
            resources.deleteById(id);
        }
    }

    private Event event(String title, long startDaysFromNow, EventStatus status) {
        Event e = new Event();
        e.setClubId("test-club-" + tag);
        e.setTitle(title + " " + tag);
        e.setResourceId("test-room");
        Instant start = Instant.now().plus(startDaysFromNow, ChronoUnit.DAYS);
        e.setStart(start);
        e.setEnd(start.plus(2, ChronoUnit.HOURS));
        e.setExpectedAttendees(10);
        e.setStatus(status);
        e = events.save(e);
        eventIds.add(e.getId());
        return e;
    }

    private void confirmed(Event e, String email) {
        Registration r = new Registration();
        r.setEventId(e.getId());
        r.setUserEmail(email);
        r.setStatus(RegistrationStatus.CONFIRMED);
        r.setConfirmedAt(Instant.now());
        registrations.save(r);
    }

    @Test
    void threeNoShowsPauseRegistrationAndAttendingCounts() {
        String email = "noshow-" + tag + "@campus.test";
        Requester student = new Requester(email, Role.STUDENT);
        Event past1 = event("Past one", -10, EventStatus.APPROVED);
        Event past2 = event("Past two", -20, EventStatus.COMPLETED);
        Event past3 = event("Past three", -30, EventStatus.APPROVED);
        Event cancelled = event("Cancelled", -5, EventStatus.CANCELLED);
        Event old = event("Too old", -90, EventStatus.APPROVED);
        for (Event e : List.of(past1, past2, past3, cancelled, old)) {
            confirmed(e, email);
        }
        Event upcoming = event("Upcoming", 10, EventStatus.APPROVED);

        var standing = registrationService.standing(email);
        assertEquals(3, standing.noShows(), "cancelled events and misses older than 60 days do not count");
        assertTrue(standing.blocked());
        assertThrows(ForbiddenException.class, () -> registrationService.register(upcoming.getId(), student));

        attendance.save(new Attendance(past1.getId(), email, past1.getStart()));
        assertEquals(2, registrationService.standing(email).noShows());
        assertFalse(registrationService.standing(email).blocked());
        assertEquals(RegistrationStatus.CONFIRMED, registrationService.register(upcoming.getId(), student).getStatus());
    }

    @Test
    void attendanceReportAddsUpEndedEvents() {
        Event past = event("Report past", -3, EventStatus.APPROVED);
        Event future = event("Report future", 3, EventStatus.APPROVED);
        for (int i = 0; i < 4; i++) {
            confirmed(past, "r" + i + "-" + tag + "@campus.test");
            confirmed(future, "r" + i + "-" + tag + "@campus.test");
        }
        attendance.save(new Attendance(past.getId(), "r0-" + tag + "@campus.test", past.getStart()));
        attendance.save(new Attendance(past.getId(), "r1-" + tag + "@campus.test", past.getStart()));
        attendance.save(new Attendance(past.getId(), "r2-" + tag + "@campus.test", past.getStart()));

        var summary = reports.attendance(Instant.now().minus(7, ChronoUnit.DAYS), Instant.now().plus(7, ChronoUnit.DAYS));
        var row = summary.byEvent().stream().filter(r -> r.eventId().equals(past.getId())).findFirst().orElseThrow();
        assertEquals(4, row.registered());
        assertEquals(3, row.attended());
        assertEquals(1, row.noShows());
        assertEquals(75.0, row.attendanceRate());
        assertTrue(summary.byEvent().stream().noneMatch(r -> r.eventId().equals(future.getId())), "future events are not reported yet");
    }

    @Test
    void utilizationCountsApprovedBookingTimeAgainstOpeningHours() {
        Resource room = new Resource();
        room.setName("Report Room " + tag);
        room.setType(ResourceType.ROOM);
        room.setCapacity(30);
        room.setOpenTime("08:00");
        room.setCloseTime("20:00");
        room = resources.save(room);
        resourceIds.add(room.getId());

        Instant dayStart = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant from = dayStart;
        Instant to = dayStart.plus(1, ChronoUnit.DAYS);
        Booking approved = new Booking();
        approved.setResourceId(room.getId());
        approved.setStart(dayStart.plus(6, ChronoUnit.HOURS));       // 11:30 IST
        approved.setEnd(dayStart.plus(9, ChronoUnit.HOURS));         // 14:30 IST: 3 hours
        approved.setStatus(BookingStatus.APPROVED);
        bookings.save(approved);
        Booking pending = new Booking();
        pending.setResourceId(room.getId());
        pending.setStart(dayStart.plus(10, ChronoUnit.HOURS));
        pending.setEnd(dayStart.plus(11, ChronoUnit.HOURS));
        pending.setStatus(BookingStatus.PENDING);
        bookings.save(pending);

        String id = room.getId();
        var usage = reports.utilization(from, to).stream().filter(u -> u.resourceId().equals(id)).findFirst().orElseThrow();
        assertEquals(1, usage.bookings(), "pending requests are not counted as use");
        assertEquals(180, usage.bookedMinutes());
        assertEquals(12 * 60, usage.openMinutes());
        assertEquals(25.0, usage.utilization());
    }
}
