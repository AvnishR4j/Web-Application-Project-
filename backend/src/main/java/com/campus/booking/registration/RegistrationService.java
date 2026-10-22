package com.campus.booking.registration;

import com.campus.booking.booking.Requester;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.FeedbackRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.support.TransactionRetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Workflow C from the plan. A student is CONFIRMED while seats remain, otherwise WAITLISTED (waitlisted
 * students do not count toward capacity). When a confirmed student cancels, the earliest waitlisted student
 * is promoted after checking capacity again. Every change runs in a transaction that first writes to the
 * event's document, so concurrent registrations for one event are serialised and can never overfill it.
 * The event's seat count is its expected attendees, which the conflict engine already checked against the room.
 */
@Service
public class RegistrationService {

    /** How long a request may wait its turn when many students register for the same event at once. */
    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    public record SeatSummary(String eventId, int capacity, long confirmed, long waitlisted,
                              RegistrationStatus myStatus, Integer myWaitlistPosition) {
    }

    public record MyRegistration(Registration registration, String eventTitle, Instant start, Instant end,
                                 EventStatus eventStatus, Integer waitlistPosition, boolean checkedIn,
                                 boolean feedbackGiven) {
    }

    public record EventRegistrations(int capacity, List<Registration> confirmed, List<Registration> waitlist) {
    }

    /** The plan's no-show rule: too many missed events in the window pauses new registrations. */
    public record Standing(long noShows, int limit, int windowDays, boolean blocked) {
    }

    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final AttendanceRepository attendance;
    private final FeedbackRepository feedback;
    private final MongoTemplate mongo;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int noShowLimit;
    private final int noShowWindowDays;

    public RegistrationService(RegistrationRepository registrations, EventRepository events,
                               AttendanceRepository attendance, FeedbackRepository feedback, MongoTemplate mongo,
                               MongoTransactionManager txManager, Clock clock,
                               @Value("${app.noshow.limit}") int noShowLimit,
                               @Value("${app.noshow.window-days}") int noShowWindowDays) {
        this.registrations = registrations;
        this.events = events;
        this.attendance = attendance;
        this.feedback = feedback;
        this.mongo = mongo;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.noShowLimit = noShowLimit;
        this.noShowWindowDays = noShowWindowDays;
    }

    /** Registering twice returns the existing place; re-registering after cancelling joins the back of the queue. */
    public Registration register(String eventId, Requester who) {
        try {
            return TransactionRetry.run(tx, MAX_WAIT, () -> registerInTransaction(eventId, who));
        } catch (DuplicateKeyException duplicate) {
            // Two first-time requests from the same student raced; the other one created the record.
            return registrations.findByEventIdAndUserEmail(eventId, who.email()).orElseThrow(() -> duplicate);
        }
    }

    private Registration registerInTransaction(String eventId, Requester who) {
        lockEvent(eventId);
        Event event = requireEvent(eventId);
        Instant now = Instant.now(clock);
        if (event.getStatus() != EventStatus.APPROVED) {
            throw new ConflictException("Registration is open only for approved events");
        }
        if (!event.getStart().isAfter(now)) {
            throw new BadRequestException("This event has already started");
        }
        Optional<Registration> existing = registrations.findByEventIdAndUserEmail(eventId, who.email());
        if (existing.isPresent() && existing.get().getStatus() != RegistrationStatus.CANCELLED) {
            return existing.get();
        }
        Standing standing = standing(who.email());
        if (standing.blocked()) {
            throw new ForbiddenException("You missed " + standing.noShows() + " events you registered for in the last "
                    + standing.windowDays() + " days, so new registrations are paused. They reopen as older misses "
                    + "pass the " + standing.windowDays() + "-day mark.");
        }
        Registration r = existing.orElseGet(Registration::new);
        r.setEventId(eventId);
        r.setUserEmail(who.email());
        r.setCancelledAt(null);
        r.setPromoted(false);
        long confirmed = registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED);
        if (confirmed < event.getExpectedAttendees()) {
            r.setStatus(RegistrationStatus.CONFIRMED);
            r.setConfirmedAt(now);
            r.setWaitlistedAt(null);
        } else {
            r.setStatus(RegistrationStatus.WAITLISTED);
            r.setWaitlistedAt(now);
            r.setConfirmedAt(null);
        }
        return registrations.save(r);
    }

    /** Cancels the caller's own place. If it was a confirmed seat, the head of the waitlist moves up. */
    public Registration cancel(String eventId, Requester who) {
        return TransactionRetry.run(tx, MAX_WAIT, () -> {
            lockEvent(eventId);
            Registration r = registrations.findByEventIdAndUserEmail(eventId, who.email())
                    .orElseThrow(() -> new NotFoundException("You are not registered for this event"));
            if (r.getStatus() == RegistrationStatus.CANCELLED) {
                return r;
            }
            Event event = requireEvent(eventId);
            Instant now = Instant.now(clock);
            if (!event.getStart().isAfter(now)) {
                throw new BadRequestException("This event has already started");
            }
            boolean freedASeat = r.getStatus() == RegistrationStatus.CONFIRMED;
            r.setStatus(RegistrationStatus.CANCELLED);
            r.setCancelledAt(now);
            Registration saved = registrations.save(r);
            if (freedASeat) {
                promoteWhileSeatsFree(event, now);
            }
            return saved;
        });
    }

    /** Called when an event is (re)approved, in case its seat count went up while waitlisted students waited. */
    public void fillFromWaitlist(String eventId) {
        TransactionRetry.run(tx, MAX_WAIT, () -> {
            lockEvent(eventId);
            Event event = requireEvent(eventId);
            if (event.getStatus() == EventStatus.APPROVED) {
                promoteWhileSeatsFree(event, Instant.now(clock));
            }
            return null;
        });
    }

    /** The second capacity check from the plan: promote only while confirmed is below capacity. */
    private void promoteWhileSeatsFree(Event event, Instant now) {
        long confirmed = registrations.countByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED);
        while (confirmed < event.getExpectedAttendees()) {
            Optional<Registration> next = registrations
                    .findFirstByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(event.getId(), RegistrationStatus.WAITLISTED);
            if (next.isEmpty()) {
                return;
            }
            Registration r = next.get();
            r.setStatus(RegistrationStatus.CONFIRMED);
            r.setPromoted(true);
            r.setConfirmedAt(now);
            registrations.save(r);
            confirmed++;
        }
    }

    /**
     * Counts events the student was registered for (confirmed), that went ahead (approved or completed), ended
     * within the window, and that they did not check in to. Cancelled events and cancelled registrations never count.
     */
    public Standing standing(String email) {
        Instant now = Instant.now(clock);
        Instant since = now.minus(Duration.ofDays(noShowWindowDays));
        List<String> eventIds = registrations.findByUserEmailAndStatus(email, RegistrationStatus.CONFIRMED).stream()
                .map(Registration::getEventId).toList();
        long missed = 0;
        for (Event e : events.findAllById(eventIds)) {
            boolean wentAhead = e.getStatus() == EventStatus.APPROVED || e.getStatus() == EventStatus.COMPLETED;
            boolean endedInWindow = e.getEnd().isBefore(now) && e.getEnd().isAfter(since);
            if (wentAhead && endedInWindow && !attendance.existsByEventIdAndUserEmail(e.getId(), email)) {
                missed++;
            }
        }
        return new Standing(missed, noShowLimit, noShowWindowDays, noShowLimit > 0 && missed >= noShowLimit);
    }

    // ----- reading -----

    /**
     * Seat counts for many events with one query: every confirmed or waitlisted registration for these events is
     * read once and counted here, rather than three queries per event.
     */
    public List<SeatSummary> summaries(List<Event> list, Requester who) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<String> ids = list.stream().map(Event::getId).toList();
        Map<String, List<Registration>> byEvent = registrations
                .findByEventIdInAndStatusIn(ids, List.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED))
                .stream().collect(Collectors.groupingBy(Registration::getEventId));
        List<SeatSummary> out = new ArrayList<>(list.size());
        for (Event e : list) {
            List<Registration> regs = byEvent.getOrDefault(e.getId(), List.of());
            long confirmed = regs.stream().filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED).count();
            List<Registration> queue = waitlistOrder(regs);
            Registration mine = regs.stream().filter(r -> who.email().equals(r.getUserEmail())).findFirst().orElse(null);
            RegistrationStatus myStatus = mine == null ? null : mine.getStatus();
            Integer position = myStatus == RegistrationStatus.WAITLISTED ? positionIn(queue, mine) : null;
            out.add(new SeatSummary(e.getId(), e.getExpectedAttendees(), confirmed, queue.size(), myStatus, position));
        }
        return out;
    }

    /** The viewer's tickets with a fixed number of queries, however many tickets they hold. */
    public List<MyRegistration> mine(Requester who) {
        List<Registration> regs = registrations.findByUserEmailOrderByCreatedAtDesc(who.email());
        if (regs.isEmpty()) {
            return List.of();
        }
        Set<String> eventIds = regs.stream().map(Registration::getEventId).collect(Collectors.toSet());
        Map<String, Event> eventsById = new HashMap<>();
        events.findAllById(eventIds).forEach(e -> eventsById.put(e.getId(), e));
        Set<String> attended = attendance.findByUserEmail(who.email()).stream()
                .map(a -> a.getEventId()).collect(Collectors.toSet());
        Set<String> reviewed = feedback.findByUserEmail(who.email()).stream()
                .map(f -> f.getEventId()).collect(Collectors.toSet());
        List<String> waitlistedEvents = regs.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .map(Registration::getEventId).distinct().toList();
        Map<String, List<Registration>> queues = waitlistedEvents.isEmpty() ? Map.of() : registrations
                .findByEventIdInAndStatusIn(waitlistedEvents, List.of(RegistrationStatus.WAITLISTED))
                .stream().collect(Collectors.groupingBy(Registration::getEventId));
        List<MyRegistration> out = new ArrayList<>(regs.size());
        for (Registration r : regs) {
            Event e = eventsById.get(r.getEventId());
            if (e == null) {
                continue;
            }
            Integer position = r.getStatus() == RegistrationStatus.WAITLISTED
                    ? positionIn(waitlistOrder(queues.getOrDefault(r.getEventId(), List.of())), r) : null;
            out.add(new MyRegistration(r, e.getTitle(), e.getStart(), e.getEnd(), e.getStatus(), position,
                    attended.contains(r.getEventId()), reviewed.contains(r.getEventId())));
        }
        return out;
    }

    /** Waitlisted registrations in queue order: earliest waitlistedAt first, ties broken by insertion order. */
    private static List<Registration> waitlistOrder(List<Registration> regs) {
        return regs.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .sorted(Comparator.comparing(Registration::getWaitlistedAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                        .thenComparing(Registration::getId))
                .toList();
    }

    private static int positionIn(List<Registration> queue, Registration r) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(r.getId())) {
                return i + 1;
            }
        }
        return queue.size();
    }

    /** For the club's organizers, its advisor and admins (access is checked by the caller). */
    public EventRegistrations forEvent(String eventId) {
        Event event = requireEvent(eventId);
        return new EventRegistrations(event.getExpectedAttendees(),
                registrations.findByEventIdAndStatusOrderByConfirmedAtAsc(eventId, RegistrationStatus.CONFIRMED),
                registrations.findByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(eventId, RegistrationStatus.WAITLISTED));
    }

    /** 1-based place in the queue. */
    private int waitlistPosition(Registration r) {
        List<Registration> queue = registrations
                .findByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(r.getEventId(), RegistrationStatus.WAITLISTED);
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(r.getId())) {
                return i + 1;
            }
        }
        return queue.size();
    }

    // ----- helpers -----

    /**
     * Writes a counter on the event's raw document (not through the Event entity, so the event's version and
     * fields are untouched). Two transactions doing this for the same event collide, and one retries.
     */
    private void lockEvent(String eventId) {
        if (!ObjectId.isValid(eventId)) {
            throw new NotFoundException("Event not found");
        }
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(eventId))),
                new Update().inc("registrationLock", 1), "events");
        if (result.getMatchedCount() == 0) {
            throw new NotFoundException("Event not found");
        }
    }

    private Event requireEvent(String eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
