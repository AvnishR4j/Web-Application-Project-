package com.campus.booking.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.campus.booking.booking.Requester;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.model.Attendance;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Role;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.FeedbackRepository;
import com.campus.booking.repository.RegistrationRepository;
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

/**
 * The Phase 5 acceptance test: a full event runs end to end (check-in, feedback, report).
 * Runs only when MONGODB_URI is set. Events and registrations are inserted directly so the test controls time.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class AttendanceFlowTest {

    @Autowired AttendanceService service;
    @Autowired RotatingCode codes;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired AttendanceRepository attendance;
    @Autowired FeedbackRepository feedback;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String id : created) {
            feedback.deleteByEventId(id);
            attendance.deleteByEventId(id);
            registrations.deleteByEventId(id);
            events.deleteById(id);
        }
    }

    /** An approved event that started {@code minutesAgo} minutes ago and lasts two hours. */
    private Event runningEvent(long minutesAgo) {
        Event e = new Event();
        e.setClubId("test-club-" + UUID.randomUUID());
        e.setTitle("Attendance test");
        e.setResourceId("test-room");
        Instant start = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);
        e.setStart(start);
        e.setEnd(start.plus(2, ChronoUnit.HOURS));
        e.setExpectedAttendees(10);
        e.setStatus(EventStatus.APPROVED);
        e = events.save(e);
        created.add(e.getId());
        return e;
    }

    private void register(Event e, String name, RegistrationStatus status) {
        Registration r = new Registration();
        r.setEventId(e.getId());
        r.setUserEmail(name + "@campus.test");
        r.setStatus(status);
        if (status == RegistrationStatus.WAITLISTED) {
            r.setWaitlistedAt(Instant.now());
        } else {
            r.setConfirmedAt(Instant.now());
        }
        registrations.save(r);
    }

    private static Requester student(String name) {
        return new Requester(name + "@campus.test", Role.STUDENT);
    }

    private String code(Event e) {
        return service.currentCode(e.getId()).code();
    }

    @Test
    void fullEventRunsEndToEnd() {
        Event e = runningEvent(10);
        register(e, "a", RegistrationStatus.CONFIRMED);
        register(e, "b", RegistrationStatus.CONFIRMED);
        register(e, "c", RegistrationStatus.CONFIRMED);

        Attendance first = service.checkIn(e.getId(), code(e), student("a"));
        Attendance again = service.checkIn(e.getId(), code(e), student("a"));
        assertEquals(first.getId(), again.getId(), "checking in twice keeps one record");
        service.checkIn(e.getId(), code(e), student("b"));

        service.giveFeedback(e.getId(), 5, "Great talk", student("a"));
        service.giveFeedback(e.getId(), 3, null, student("b"));

        var report = service.report(e.getId());
        assertEquals(3, report.registered());
        assertEquals(2, report.attended());
        assertEquals(1, report.noShows());
        assertEquals(66.7, report.attendanceRate());
        assertEquals(2, report.feedbackCount());
        assertEquals(4.0, report.averageRating());
        assertEquals(1, report.comments().size(), "only feedback with a comment is listed");
        assertEquals(2, service.currentCode(e.getId()).checkedIn());
    }

    @Test
    void wrongOrOldCodeIsRejected() {
        Event e = runningEvent(10);
        register(e, "a", RegistrationStatus.CONFIRMED);
        long window = RotatingCode.windowOf(Instant.now());
        String old = codes.codeFor(e.getId(), window - 3);
        assertThrows(BadRequestException.class, () -> service.checkIn(e.getId(), old, student("a")));
        assertThrows(BadRequestException.class, () -> service.checkIn(e.getId(), "ZZZZZZ", student("a")));
        assertEquals(0, attendance.countByEventId(e.getId()));
    }

    @Test
    void onlyConfirmedRegistrantsCanCheckIn() {
        Event e = runningEvent(10);
        register(e, "w", RegistrationStatus.WAITLISTED);
        register(e, "x", RegistrationStatus.CANCELLED);
        String code = code(e);
        assertThrows(ForbiddenException.class, () -> service.checkIn(e.getId(), code, student("w")));
        assertThrows(ForbiddenException.class, () -> service.checkIn(e.getId(), code, student("x")));
        assertThrows(ForbiddenException.class, () -> service.checkIn(e.getId(), code, student("stranger")));
    }

    @Test
    void checkInIsOnlyOpenAroundTheEvent() {
        Event future = runningEvent(-24 * 60);          // starts tomorrow
        register(future, "a", RegistrationStatus.CONFIRMED);
        String code = codes.current(future.getId(), Instant.now());
        assertThrows(BadRequestException.class, () -> service.checkIn(future.getId(), code, student("a")));
        assertThrows(BadRequestException.class, () -> service.currentCode(future.getId()));

        Event soon = runningEvent(-20);                 // starts in 20 minutes: inside the 30-minute opening
        register(soon, "a", RegistrationStatus.CONFIRMED);
        service.checkIn(soon.getId(), code(soon), student("a"));

        Event ended = runningEvent(3 * 60);             // ended an hour ago
        register(ended, "a", RegistrationStatus.CONFIRMED);
        String endedCode = codes.current(ended.getId(), Instant.now());
        assertThrows(BadRequestException.class, () -> service.checkIn(ended.getId(), endedCode, student("a")));
    }

    @Test
    void onlyAttendeesGiveFeedbackAndOnlyOnce() {
        Event e = runningEvent(10);
        register(e, "a", RegistrationStatus.CONFIRMED);
        register(e, "b", RegistrationStatus.CONFIRMED);
        service.checkIn(e.getId(), code(e), student("a"));

        assertThrows(ForbiddenException.class, () -> service.giveFeedback(e.getId(), 4, "hi", student("b")));
        assertThrows(BadRequestException.class, () -> service.giveFeedback(e.getId(), 6, null, student("a")));
        service.giveFeedback(e.getId(), 4, "Good", student("a"));
        assertThrows(ConflictException.class, () -> service.giveFeedback(e.getId(), 5, "again", student("a")));
    }

    @Test
    void unapprovedEventsHaveNoCheckIn() {
        Event e = runningEvent(10);
        Event reloaded = events.findById(e.getId()).orElseThrow();
        reloaded.setStatus(EventStatus.CANCELLED);
        events.save(reloaded);
        assertThrows(ConflictException.class, () -> service.currentCode(e.getId()));
    }
}
