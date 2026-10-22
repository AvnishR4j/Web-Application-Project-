package com.campus.booking.booking;

import com.campus.booking.booking.ConflictEngine.Proposal;
import com.campus.booking.booking.ConflictEngine.Violation;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.BlockedPeriod;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.repository.BlockedPeriodRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ResourceRepository;
import com.campus.booking.repository.TrainingRecordRepository;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private static final int MAX_ATTEMPTS = 25;
    static final Duration LATE_CANCEL_WINDOW = Duration.ofHours(72);

    public record BookingInput(String resourceId, String title, Instant start, Instant end, int attendees) {
    }

    public record CheckResult(boolean available, List<Violation> violations) {
    }

    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final BlockedPeriodRepository blockedPeriods;
    private final TrainingRecordRepository training;
    private final MongoTemplate mongo;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public BookingService(ResourceRepository resources, BookingRepository bookings,
                          BlockedPeriodRepository blockedPeriods, TrainingRecordRepository training,
                          MongoTemplate mongo, MongoTransactionManager txManager, Clock clock,
                          @Value("${app.booking.zone}") String zone) {
        this.resources = resources;
        this.bookings = bookings;
        this.blockedPeriods = blockedPeriods;
        this.training = training;
        this.mongo = mongo;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** Dry run: runs the same checks as a real booking without saving anything. */
    public CheckResult check(BookingInput in, Requester who) {
        Resource resource = requireResource(in.resourceId());
        List<Violation> violations = evaluate(resource, in, who);
        return new CheckResult(violations.isEmpty(), violations);
    }

    /**
     * Creates a PENDING booking. The check and the insert happen in one transaction, and the transaction
     * also bumps the resource document, so concurrent requests for the same resource collide and one of
     * them retries and then sees the other's booking. Repeating the same idempotency key returns the
     * original booking instead of creating a second one.
     */
    public Booking create(BookingInput in, String clientKey, Requester who) {
        return create(in, clientKey, who, null);
    }

    /** Same as {@link #create(BookingInput, String, Requester)}, linking the booking to an event when eventId is set. */
    public Booking create(BookingInput in, String clientKey, Requester who, String eventId) {
        String key = who.email() + ":" + clientKey;
        for (int attempt = 1; ; attempt++) {
            Optional<Booking> prior = bookings.findByIdempotencyKey(key);
            if (prior.isPresent()) {
                return prior.get();
            }
            try {
                // Cheap rejection first: once someone else holds the slot, losers leave here without
                // opening a transaction, so they stop colliding with each other on retries.
                List<Violation> early = evaluate(requireResource(in.resourceId()), in, who);
                if (!early.isEmpty()) {
                    throw new BookingConflictException(early);
                }
                return tx.execute(status -> createInTransaction(in, key, who, eventId));
            } catch (DuplicateKeyException duplicate) {
                Optional<Booking> winner = bookings.findByIdempotencyKey(key);
                if (winner.isPresent()) {
                    return winner.get();
                }
                throw duplicate;
            } catch (BookingConflictException conflict) {
                // The "conflict" may be this same request's own booking, committed by a parallel retry.
                Optional<Booking> mine = bookings.findByIdempotencyKey(key);
                if (mine.isPresent()) {
                    return mine.get();
                }
                throw conflict;
            } catch (RuntimeException e) {
                if (!isTransient(e)) {
                    throw e;
                }
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ConflictException("The resource is busy right now. Please try again.");
                }
                backoff(attempt);
            }
        }
    }

    private Booking createInTransaction(BookingInput in, String key, Requester who, String eventId) {
        Resource resource = requireResource(in.resourceId());

        // Serialise bookings per resource: a second transaction touching this document fails with a write conflict.
        mongo.updateFirst(Query.query(Criteria.where("id").is(resource.getId())),
                new Update().inc("lockVersion", 1), Resource.class);

        List<Violation> violations = evaluate(resource, in, who);
        if (!violations.isEmpty()) {
            throw new BookingConflictException(violations);
        }

        Booking booking = new Booking();
        booking.setResourceId(resource.getId());
        booking.setRequesterEmail(who.email());
        booking.setTitle(in.title());
        booking.setStart(in.start());
        booking.setEnd(in.end());
        booking.setAttendees(in.attendees());
        booking.setStatus(BookingStatus.PENDING);
        booking.setIdempotencyKey(key);
        booking.setEventId(eventId);
        return bookings.insert(booking);
    }

    public Booking approve(String bookingId, String adminEmail) {
        requireNotEventBooking(requireBooking(bookingId));
        return decide(bookingId, adminEmail, BookingStatus.APPROVED, null);
    }

    public Booking reject(String bookingId, String adminEmail, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required when rejecting a booking");
        }
        requireNotEventBooking(requireBooking(bookingId));
        return decide(bookingId, adminEmail, BookingStatus.REJECTED, reason.trim());
    }

    /** Used by the event workflow once the space manager approves the event. */
    public Booking approveForEvent(String bookingId, String actorEmail) {
        return decide(bookingId, actorEmail, BookingStatus.APPROVED, null);
    }

    /**
     * Used by the event workflow to free the venue when an event is rejected, cancelled or edited.
     * Does nothing if the booking no longer holds the slot.
     */
    public void releaseForEvent(String bookingId, String actorEmail, BookingStatus target, String reason) {
        if (bookingId == null) {
            return;
        }
        mongo.updateFirst(
                Query.query(Criteria.where("id").is(bookingId)
                        .and("status").in(BookingStatus.PENDING, BookingStatus.APPROVED)),
                new Update().set("status", target).set("decidedBy", actorEmail)
                        .set("decidedAt", Instant.now(clock)).set("decisionReason", reason),
                Booking.class);
    }

    private static void requireNotEventBooking(Booking booking) {
        if (booking.getEventId() != null) {
            throw new ConflictException("This booking belongs to an event. Approve, reject or cancel the event instead.");
        }
    }

    private Booking decide(String bookingId, String adminEmail, BookingStatus target, String reason) {
        requireBooking(bookingId);
        // Conditional update: only a PENDING booking can be decided, and only once.
        var result = mongo.updateFirst(
                Query.query(Criteria.where("id").is(bookingId).and("status").is(BookingStatus.PENDING)),
                new Update().set("status", target).set("decidedBy", adminEmail)
                        .set("decidedAt", Instant.now(clock)).set("decisionReason", reason),
                Booking.class);
        if (result.getModifiedCount() == 0) {
            throw new ConflictException("This booking has already been decided or cancelled");
        }
        return requireBooking(bookingId);
    }

    public Booking cancel(String bookingId, Requester who) {
        Booking booking = requireBooking(bookingId);
        requireNotEventBooking(booking);
        if (!who.isResourceManager() && !booking.getRequesterEmail().equals(who.email())) {
            throw new ForbiddenException("You can only cancel your own bookings");
        }
        Instant now = Instant.now(clock);
        if (!booking.getStart().isAfter(now)) {
            throw new BadRequestException("A booking that has already started cannot be cancelled");
        }
        boolean late = booking.getStart().isBefore(now.plus(LATE_CANCEL_WINDOW));
        var result = mongo.updateFirst(
                Query.query(Criteria.where("id").is(bookingId)
                        .and("status").in(BookingStatus.PENDING, BookingStatus.APPROVED)),
                new Update().set("status", BookingStatus.CANCELLED).set("lateCancellation", late)
                        .set("decidedBy", who.email()).set("decidedAt", now),
                Booking.class);
        if (result.getModifiedCount() == 0) {
            throw new ConflictException("This booking was already cancelled or rejected");
        }
        return requireBooking(bookingId);
    }

    /** Bookings that hold a slot on one resource in a time window, for the availability view. */
    public List<Booking> schedule(String resourceId, Instant from, Instant to) {
        return bookings.findByResourceIdAndStatusInAndStartLessThanAndEndGreaterThan(
                resourceId, BookingStatus.ACTIVE, to, from);
    }

    private List<Violation> evaluate(Resource resource, BookingInput in, Requester who) {
        boolean hasAccess = who.isResourceManager()
                || training.existsByUserEmailAndResourceId(who.email(), resource.getId());
        Proposal proposal = new Proposal(in.start(), in.end(), in.attendees(), hasAccess);

        if (in.start() == null || in.end() == null || !in.end().isAfter(in.start())) {
            return ConflictEngine.evaluate(resource, proposal, List.of(), List.of(), zone, Instant.now(clock));
        }

        // Look a little wider than the proposed window so buffers on either side are caught.
        Duration reach = Duration.ofMinutes(Math.max(0, resource.getBufferMinutes()));
        Instant from = in.start().minus(reach);
        Instant to = in.end().plus(reach);
        List<Booking> nearby = bookings.findByResourceIdAndStatusInAndStartLessThanAndEndGreaterThan(
                resource.getId(), BookingStatus.ACTIVE, to, from);

        List<BlockedPeriod> blocked = new ArrayList<>(
                blockedPeriods.findByResourceIdAndStartLessThanAndEndGreaterThan(resource.getId(), in.end(), in.start()));
        blocked.addAll(blockedPeriods.findByResourceIdIsNullAndStartLessThanAndEndGreaterThan(in.end(), in.start()));

        return ConflictEngine.evaluate(resource, proposal, nearby, blocked, zone, Instant.now(clock));
    }

    private Resource requireResource(String id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Resource not found"));
    }

    private Booking requireBooking(String id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    /** MongoDB marks write conflicts and similar retryable transaction failures with a label. */
    static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof MongoException me
                    && (me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || me.getCode() == 112 /* WriteConflict */)) {
                return true;
            }
        }
        return false;
    }

    private static void backoff(int attempt) {
        long max = Math.min(300, 15L * attempt);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, max + 5));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Interrupted while waiting to retry");
        }
    }
}
