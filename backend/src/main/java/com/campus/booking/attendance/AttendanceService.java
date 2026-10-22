package com.campus.booking.attendance;

import com.campus.booking.booking.Requester;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.Attendance;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Feedback;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.FeedbackRepository;
import com.campus.booking.repository.RegistrationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Workflow D from the plan: rotating-code check-in for confirmed registrants, feedback only from people who
 * checked in, and a registered-versus-attended report.
 */
@Service
public class AttendanceService {

    /** Feedback stays open this long after the event ends. */
    static final Duration FEEDBACK_WINDOW = Duration.ofDays(14);

    public record CheckinCode(String code, Instant expiresAt, int windowSeconds, long checkedIn, long registered) {
    }

    public record RatingCount(int rating, long count) {
    }

    /** Comments are shown without names so students can be honest. */
    public record Comment(int rating, String comment, Instant at) {
    }

    public record Report(String eventId, String title, EventStatus status, int seats, long registered, long waitlisted,
                         long attended, long noShows, Double attendanceRate, long feedbackCount, Double averageRating,
                         List<RatingCount> ratings, List<Comment> comments, List<Attendance> attendees) {
    }

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final FeedbackRepository feedback;
    private final RotatingCode codes;
    private final Clock clock;
    private final Duration opensBefore;

    public AttendanceService(EventRepository events, RegistrationRepository registrations,
                             AttendanceRepository attendance, FeedbackRepository feedback, RotatingCode codes,
                             Clock clock, @Value("${app.checkin.opens-minutes-before}") long opensMinutesBefore) {
        this.events = events;
        this.registrations = registrations;
        this.attendance = attendance;
        this.feedback = feedback;
        this.codes = codes;
        this.clock = clock;
        this.opensBefore = Duration.ofMinutes(opensMinutesBefore);
    }

    /** The code to show on the organizer's screen. The caller must already be an event insider. */
    public CheckinCode currentCode(String eventId) {
        Event e = requireEvent(eventId);
        Instant now = Instant.now(clock);
        requireCheckinOpen(e, now);
        return new CheckinCode(codes.current(eventId, now), RotatingCode.expiresAt(now), RotatingCode.WINDOW_SECONDS,
                attendance.countByEventId(eventId),
                registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED));
    }

    /** Checking in twice is harmless: the first check-in is returned. */
    public Attendance checkIn(String eventId, String code, Requester who) {
        Event e = requireEvent(eventId);
        Instant now = Instant.now(clock);
        requireCheckinOpen(e, now);
        Registration r = registrations.findByEventIdAndUserEmail(eventId, who.email()).orElse(null);
        if (r == null || r.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new ForbiddenException(r != null && r.getStatus() == RegistrationStatus.WAITLISTED
                    ? "You are still on the waitlist, so you cannot check in"
                    : "Only registered students can check in");
        }
        var existing = attendance.findByEventIdAndUserEmail(eventId, who.email());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!codes.matches(eventId, code, now)) {
            throw new BadRequestException("That code has expired or is wrong. Scan the code on screen again.");
        }
        try {
            return attendance.save(new Attendance(eventId, who.email(), now));
        } catch (DuplicateKeyException raced) {
            return attendance.findByEventIdAndUserEmail(eventId, who.email()).orElseThrow(() -> raced);
        }
    }

    public Feedback giveFeedback(String eventId, int rating, String comment, Requester who) {
        Event e = requireEvent(eventId);
        Instant now = Instant.now(clock);
        if (!attendance.existsByEventIdAndUserEmail(eventId, who.email())) {
            throw new ForbiddenException("Only students who checked in can give feedback");
        }
        if (now.isAfter(e.getEnd().plus(FEEDBACK_WINDOW))) {
            throw new BadRequestException("Feedback for this event has closed");
        }
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Rating must be between 1 and 5");
        }
        if (feedback.existsByEventIdAndUserEmail(eventId, who.email())) {
            throw new ConflictException("You have already given feedback for this event");
        }
        Feedback f = new Feedback();
        f.setEventId(eventId);
        f.setUserEmail(who.email());
        f.setRating(rating);
        f.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        f.setCreatedAt(now);
        try {
            return feedback.save(f);
        } catch (DuplicateKeyException raced) {
            throw new ConflictException("You have already given feedback for this event");
        }
    }

    /** Registered versus attended, no-shows, and ratings. The caller must already be an event insider. */
    public Report report(String eventId) {
        Event e = requireEvent(eventId);
        long registered = registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED);
        long waitlisted = registrations.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED);
        List<Attendance> attendees = attendance.findByEventIdOrderByCheckedInAtAsc(eventId);
        long attended = attendees.size();
        boolean started = !e.getStart().isAfter(Instant.now(clock));
        long noShows = started ? Math.max(0, registered - attended) : 0;
        List<Feedback> all = feedback.findByEventIdOrderByCreatedAtDesc(eventId);
        Double average = all.isEmpty() ? null
                : Math.round(all.stream().mapToInt(Feedback::getRating).average().orElse(0) * 10) / 10.0;
        List<RatingCount> ratings = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(n -> new RatingCount(n, all.stream().filter(f -> f.getRating() == n).count()))
                .toList();
        List<Comment> comments = all.stream().filter(f -> f.getComment() != null)
                .map(f -> new Comment(f.getRating(), f.getComment(), f.getCreatedAt())).toList();
        Double rate = registered == 0 ? null : Math.round(attended * 1000.0 / registered) / 10.0;
        return new Report(eventId, e.getTitle(), e.getStatus(), e.getExpectedAttendees(), registered, waitlisted,
                attended, noShows, rate, all.size(), average, ratings, comments, attendees);
    }

    public boolean hasCheckedIn(String eventId, String email) {
        return attendance.existsByEventIdAndUserEmail(eventId, email);
    }

    public boolean hasGivenFeedback(String eventId, String email) {
        return feedback.existsByEventIdAndUserEmail(eventId, email);
    }

    /** Check-in opens a little before the start and closes when the event ends. */
    private void requireCheckinOpen(Event e, Instant now) {
        if (e.getStatus() != EventStatus.APPROVED && e.getStatus() != EventStatus.COMPLETED) {
            throw new ConflictException("Check-in is only for approved events");
        }
        if (now.isBefore(e.getStart().minus(opensBefore))) {
            throw new BadRequestException("Check-in opens " + opensBefore.toMinutes() + " minutes before the event starts");
        }
        if (now.isAfter(e.getEnd())) {
            throw new BadRequestException("Check-in has closed: the event has ended");
        }
    }

    private Event requireEvent(String id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
