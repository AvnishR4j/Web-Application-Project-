package com.campus.booking.config;

import com.campus.booking.model.Attendance;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Club;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.AuditLogRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.FeedbackRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.repository.ResourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Clears everything that people create (events, bookings, registrations, check-ins, feedback and the audit log)
 * and sets up a clean, realistic set of demo events dated from today. Users, rooms, clubs and training records
 * are kept. Runs at start-up only when RESET_DEMO_DATA=true and SEED_DEMO_DATA=true, so it never fires by accident:
 * switch it on, restart, then switch it off again.
 */
@Component
@Order(2) // after DemoDataSeeder, which creates the rooms and the club
public class DemoDataReset implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataReset.class);

    private final boolean reset;
    private final boolean seeded;
    private final ZoneId zone;
    private final EventRepository events;
    private final BookingRepository bookings;
    private final RegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final FeedbackRepository feedback;
    private final AuditLogRepository audit;
    private final ResourceRepository resources;
    private final ClubRepository clubs;

    public DemoDataReset(@Value("${app.reset-demo-data}") boolean reset, @Value("${app.seed-demo-data}") boolean seeded,
                         @Value("${app.booking.zone}") String zone, EventRepository events, BookingRepository bookings,
                         RegistrationRepository registrations, AttendanceRepository attendance,
                         FeedbackRepository feedback, AuditLogRepository audit, ResourceRepository resources,
                         ClubRepository clubs) {
        this.reset = reset;
        this.seeded = seeded;
        this.zone = ZoneId.of(zone);
        this.events = events;
        this.bookings = bookings;
        this.registrations = registrations;
        this.attendance = attendance;
        this.feedback = feedback;
        this.audit = audit;
        this.resources = resources;
        this.clubs = clubs;
    }

    @Override
    public void run(String... args) {
        if (!reset) {
            return;
        }
        if (!seeded) {
            log.warn("RESET_DEMO_DATA is on but SEED_DEMO_DATA is not; nothing was reset.");
            return;
        }
        Club club = clubs.findAll().stream().filter((c) -> "Coding Club".equals(c.getName())).findFirst().orElse(null);
        Resource hall = resources.findAll().stream().filter((r) -> "Seminar Hall".equals(r.getName())).findFirst().orElse(null);
        Resource lab = resources.findAll().stream().filter((r) -> "Computer Lab 1".equals(r.getName())).findFirst().orElse(null);
        if (club == null || hall == null || lab == null) {
            log.warn("RESET_DEMO_DATA: the demo club or rooms are missing; nothing was reset.");
            return;
        }

        attendance.deleteAll();
        feedback.deleteAll();
        registrations.deleteAll();
        events.deleteAll();
        bookings.deleteAll();
        audit.deleteAll();

        Instant now = Instant.now();

        // Running now: check-in is open, so the QR code can be shown and scanned at any time of day.
        Event live = approved(club, hall, "Live check-in demo",
                "Happening now. Register, then scan the QR code on the organizer's check-in screen.",
                now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofHours(11)), 60);
        register(live, "student2@campus.test", RegistrationStatus.CONFIRMED);

        Event cloud = approved(club, hall, "Intro to cloud computing",
                "A beginner session on servers, databases and deploying a web app.",
                at(3, 11, 0), at(3, 12, 30), 100);
        register(cloud, "student@campus.test", RegistrationStatus.CONFIRMED);
        register(cloud, "student2@campus.test", RegistrationStatus.CONFIRMED);

        approved(club, hall, "Hackathon kickoff",
                "Team formation and a walkthrough of the problem statements. Bring a laptop.",
                at(5, 10, 0), at(5, 13, 0), 120);

        // Full on purpose: two seats, both taken, so the next student joins the waitlist.
        Event workshop = approved(club, lab, "Robotics workshop (2 seats)",
                "A small hands-on session. It is full, so new registrations join the waitlist.",
                at(4, 14, 0), at(4, 16, 0), 2);
        register(workshop, "student2@campus.test", RegistrationStatus.CONFIRMED);
        register(workshop, "student3@campus.test", RegistrationStatus.CONFIRMED);

        // Already over, with check-ins, so the attendance report and the no-show rule have something to show.
        Event past = approved(club, hall, "Tech talk: careers in software",
                "A guest talk, held earlier this week.", at(-2, 15, 0), at(-2, 16, 30), 80);
        past.setStatus(EventStatus.COMPLETED);
        events.save(past);
        register(past, "student@campus.test", RegistrationStatus.CONFIRMED);
        register(past, "student2@campus.test", RegistrationStatus.CONFIRMED);
        register(past, "student3@campus.test", RegistrationStatus.CONFIRMED);
        checkIn(past, "student@campus.test", past.getStart().plus(Duration.ofMinutes(4)));
        checkIn(past, "student2@campus.test", past.getStart().plus(Duration.ofMinutes(9)));

        log.warn("RESET_DEMO_DATA: demo data reset. Set RESET_DEMO_DATA back to false so it does not run again.");
    }

    /** The given local time, this many days from today (negative for the past), in the campus zone. */
    private Instant at(int days, int hour, int minute) {
        LocalDate day = LocalDate.now(zone).plusDays(days);
        return ZonedDateTime.of(day, LocalTime.of(hour, minute), zone).toInstant();
    }

    private Event approved(Club club, Resource room, String title, String description, Instant start, Instant end,
                           int seats) {
        String organizer = club.getOrganizerEmails().isEmpty() ? "organizer@campus.test" : club.getOrganizerEmails().get(0);
        Event e = new Event();
        e.setClubId(club.getId());
        e.setTitle(title);
        e.setDescription(description);
        e.setResourceId(room.getId());
        e.setStart(start);
        e.setEnd(end);
        e.setExpectedAttendees(seats);
        e.setStatus(EventStatus.APPROVED);
        e.setCreatedBy(organizer);
        e.setSubmissionCount(1);
        e.setSubmittedAt(start.minus(Duration.ofDays(10)));
        e.setAdvisorDecisionBy(club.getAdvisorEmail());
        e.setAdvisorDecisionAt(start.minus(Duration.ofDays(9)));
        e.setManagerDecisionBy("resources@campus.test");
        e.setManagerDecisionAt(start.minus(Duration.ofDays(8)));
        e = events.save(e);

        // The room booking behind the event, so the timelines and conflict checks see it.
        Booking b = new Booking();
        b.setResourceId(room.getId());
        b.setRequesterEmail(organizer);
        b.setEventId(e.getId());
        b.setTitle(title);
        b.setStart(start);
        b.setEnd(end);
        b.setAttendees(seats);
        b.setStatus(BookingStatus.APPROVED);
        b = bookings.save(b);
        e.setBookingId(b.getId());
        return events.save(e);
    }

    private void register(Event e, String email, RegistrationStatus status) {
        Registration r = new Registration();
        r.setEventId(e.getId());
        r.setUserEmail(email);
        r.setStatus(status);
        Instant when = Instant.now();
        r.setCreatedAt(when);
        if (status == RegistrationStatus.CONFIRMED) {
            r.setConfirmedAt(when);
        } else {
            r.setWaitlistedAt(when);
        }
        registrations.save(r);
    }

    private void checkIn(Event e, String email, Instant when) {
        Attendance a = new Attendance();
        a.setEventId(e.getId());
        a.setUserEmail(email);
        a.setCheckedInAt(when);
        attendance.save(a);
    }
}
