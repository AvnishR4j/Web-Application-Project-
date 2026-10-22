package com.campus.booking.event;

import com.campus.booking.booking.BookingService;
import com.campus.booking.booking.BookingService.BookingInput;
import com.campus.booking.booking.Requester;
import com.campus.booking.dto.EventDtos.EventRequest;
import com.campus.booking.event.EventStateMachine.Action;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.AuditLog;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Club;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Role;
import com.campus.booking.repository.AuditLogRepository;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.ResourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Workflow A from the plan: an organizer drafts an event, submitting it reserves the venue through the
 * conflict engine (as a pending booking), the club's faculty advisor approves, then the space manager
 * approves and the booking is confirmed. Rejecting, cancelling or editing releases the venue. Every change
 * is written to the audit log.
 */
@Service
public class EventService {

    private final EventRepository events;
    private final ClubRepository clubs;
    private final ResourceRepository resources;
    private final AuditLogRepository audit;
    private final BookingService bookings;
    private final Clock clock;
    private final int minLeadDays;

    public EventService(EventRepository events, ClubRepository clubs, ResourceRepository resources,
                        AuditLogRepository audit, BookingService bookings, Clock clock,
                        @Value("${app.events.min-lead-days}") int minLeadDays) {
        this.events = events;
        this.clubs = clubs;
        this.resources = resources;
        this.audit = audit;
        this.bookings = bookings;
        this.clock = clock;
        this.minLeadDays = minLeadDays;
    }

    public int minLeadDays() {
        return minLeadDays;
    }

    // ----- organizer actions -----

    public Event create(EventRequest req, Requester who) {
        Club club = requireClub(req.clubId());
        requireOrganizer(club, who);
        Event e = new Event();
        e.setClubId(club.getId());
        e.setCreatedBy(who.email());
        apply(e, req);
        e.setStatus(EventStatus.DRAFT);
        Event saved = events.save(e);
        log(saved, "CREATED", null, EventStatus.DRAFT, who, null);
        return saved;
    }

    /** Editing anything after submission sends the event back to draft and frees the room: it needs approval again. */
    public Event update(String id, EventRequest req, Requester who) {
        Event e = requireEvent(id);
        Club club = requireClub(e.getClubId());
        requireOrganizer(club, who);
        if (!req.clubId().equals(e.getClubId())) {
            throw new BadRequestException("An event cannot be moved to another club");
        }
        requireNotStarted(e);
        EventStatus from = e.getStatus();
        EventStatus to = transition(e, Action.EDIT, "edit");
        apply(e, req); // validates first, so a bad edit never releases the room
        if (EventStateMachine.holdsRoom(from)) {
            bookings.releaseForEvent(e.getBookingId(), who.email(), BookingStatus.CANCELLED, "Event edited");
            e.setBookingId(null);
        }
        e.setStatus(to);
        e.setRejectionReason(null);
        Event saved = save(e);
        log(saved, "EDITED", from, to, who,
                EventStateMachine.holdsRoom(from) ? "Edited after submission; needs approval again" : null);
        return saved;
    }

    public Event submit(String id, Requester who) {
        Event e = requireEvent(id);
        requireOrganizer(requireClub(e.getClubId()), who);
        EventStatus from = e.getStatus();
        EventStatus to = transition(e, Action.SUBMIT, "submit");
        Instant now = Instant.now(clock);
        if (!EventStateMachine.meetsLeadTime(e.getStart(), now, minLeadDays)) {
            throw new BadRequestException("Events must be requested at least " + minLeadDays
                    + " days before they start. Pick a later date.");
        }
        // Reserve the venue: the full conflict engine runs here and a 409 lists anything in the way.
        int submission = e.getSubmissionCount() + 1;
        Booking hold = bookings.create(
                new BookingInput(e.getResourceId(), e.getTitle(), e.getStart(), e.getEnd(), e.getExpectedAttendees()),
                "event-" + e.getId() + "-" + submission, who, e.getId());
        e.setSubmissionCount(submission);
        e.setBookingId(hold.getId());
        e.setStatus(to);
        e.setSubmittedAt(now);
        e.setRejectionReason(null);
        Event saved = save(e);
        log(saved, "SUBMITTED", from, to, who, null);
        return saved;
    }

    public Event cancel(String id, String reason, Requester who) {
        Event e = requireEvent(id);
        Club club = requireClub(e.getClubId());
        if (who.role() != Role.SUPER_ADMIN) {
            requireOrganizer(club, who);
        }
        EventStatus from = e.getStatus();
        EventStatus to = transition(e, Action.CANCEL, "cancel");
        if (EventStateMachine.holdsRoom(from)) {
            bookings.releaseForEvent(e.getBookingId(), who.email(), BookingStatus.CANCELLED, "Event cancelled");
        }
        e.setStatus(to);
        e.setCancellationReason(reason == null || reason.isBlank() ? null : reason.trim());
        Event saved = save(e);
        log(saved, "CANCELLED", from, to, who, saved.getCancellationReason());
        return saved;
    }

    public Event complete(String id, Requester who) {
        Event e = requireEvent(id);
        if (who.role() != Role.SUPER_ADMIN) {
            requireOrganizer(requireClub(e.getClubId()), who);
        }
        if (e.getEnd().isAfter(Instant.now(clock))) {
            throw new BadRequestException("An event can be marked completed only after it ends");
        }
        EventStatus from = e.getStatus();
        e.setStatus(transition(e, Action.COMPLETE, "complete"));
        Event saved = save(e);
        log(saved, "COMPLETED", from, saved.getStatus(), who, null);
        return saved;
    }

    // ----- approvals -----

    public Event advisorApprove(String id, Requester who) {
        Event e = requireEvent(id);
        requireAdvisor(requireClub(e.getClubId()), who);
        EventStatus from = e.getStatus();
        e.setStatus(transition(e, Action.ADVISOR_APPROVE, "approve"));
        e.setAdvisorDecisionBy(who.email());
        e.setAdvisorDecisionAt(Instant.now(clock));
        Event saved = save(e);
        log(saved, "ADVISOR_APPROVED", from, saved.getStatus(), who, null);
        return saved;
    }

    public Event advisorReject(String id, String reason, Requester who) {
        Event e = requireEvent(id);
        requireAdvisor(requireClub(e.getClubId()), who);
        return reject(e, Action.ADVISOR_REJECT, "ADVISOR_REJECTED", reason, who, true);
    }

    public Event managerApprove(String id, Requester who) {
        Event e = requireEvent(id);
        requireManager(who);
        EventStatus from = e.getStatus();
        EventStatus to = transition(e, Action.MANAGER_APPROVE, "approve");
        bookings.approveForEvent(e.getBookingId(), who.email());
        e.setStatus(to);
        e.setManagerDecisionBy(who.email());
        e.setManagerDecisionAt(Instant.now(clock));
        Event saved = save(e);
        log(saved, "APPROVED", from, to, who, null);
        return saved;
    }

    public Event managerReject(String id, String reason, Requester who) {
        Event e = requireEvent(id);
        requireManager(who);
        return reject(e, Action.MANAGER_REJECT, "MANAGER_REJECTED", reason, who, false);
    }

    private Event reject(Event e, Action action, String logAction, String reason, Requester who, boolean advisor) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required when rejecting an event");
        }
        EventStatus from = e.getStatus();
        EventStatus to = transition(e, action, "reject");
        bookings.releaseForEvent(e.getBookingId(), who.email(), BookingStatus.REJECTED, reason.trim());
        e.setStatus(to);
        e.setRejectionReason(reason.trim());
        if (advisor) {
            e.setAdvisorDecisionBy(who.email());
            e.setAdvisorDecisionAt(Instant.now(clock));
        } else {
            e.setManagerDecisionBy(who.email());
            e.setManagerDecisionAt(Instant.now(clock));
        }
        Event saved = save(e);
        log(saved, logAction, from, to, who, reason.trim());
        return saved;
    }

    // ----- reading -----

    /** What students see: approved events that have not ended yet. */
    public List<Event> published() {
        return events.findByStatusAndEndGreaterThanOrderByStartAsc(EventStatus.APPROVED, Instant.now(clock));
    }

    public List<Event> forOrganizer(Requester who) {
        List<String> ids = clubs.findByOrganizer(who.email()).stream().map(Club::getId).toList();
        return ids.isEmpty() ? List.of() : events.findByClubIdInOrderByStartDesc(ids);
    }

    public List<Event> advisorQueue(Requester who) {
        if (who.role() == Role.SUPER_ADMIN) {
            return events.findByStatusOrderByStartAsc(EventStatus.SUBMITTED);
        }
        List<String> ids = clubs.findByAdvisorEmail(who.email()).stream().map(Club::getId).toList();
        return ids.isEmpty() ? List.of() : events.findByClubIdInAndStatusOrderByStartAsc(ids, EventStatus.SUBMITTED);
    }

    public List<Event> managerQueue() {
        return events.findByStatusOrderByStartAsc(EventStatus.ADVISOR_APPROVED);
    }

    public Event view(String id, Requester who) {
        Event e = requireEvent(id);
        if (!EventStateMachine.isPublic(e.getStatus()) && !canSeeInternals(e, who)) {
            throw new NotFoundException("Event not found");
        }
        return e;
    }

    public List<AuditLog> history(String id, Requester who) {
        Event e = requireEvent(id);
        if (!canSeeInternals(e, who)) {
            throw new ForbiddenException("Only the club's organizers, its advisor and admins can see the history");
        }
        return audit.findByEventIdOrderByAtAsc(id);
    }

    private boolean canSeeInternals(Event e, Requester who) {
        if (who.isResourceManager()) {
            return true;
        }
        Club club = clubs.findById(e.getClubId()).orElse(null);
        return club != null && (club.isOrganizer(who.email()) || who.email().equals(club.getAdvisorEmail()));
    }

    // ----- helpers -----

    private void apply(Event e, EventRequest req) {
        if (!req.end().isAfter(req.start())) {
            throw new BadRequestException("The end time must be after the start time");
        }
        if (!resources.existsById(req.resourceId())) {
            throw new NotFoundException("Venue not found");
        }
        e.setTitle(req.title().trim());
        e.setDescription(req.description());
        e.setResourceId(req.resourceId());
        e.setStart(req.start());
        e.setEnd(req.end());
        e.setExpectedAttendees(req.expectedAttendees());
        e.setUpdatedAt(Instant.now(clock));
    }

    private static EventStatus transition(Event e, Action action, String verb) {
        return EventStateMachine.next(e.getStatus(), action).orElseThrow(() -> new ConflictException(
                "Cannot " + verb + " an event that is " + e.getStatus().name().toLowerCase().replace('_', ' ')));
    }

    private void requireNotStarted(Event e) {
        if (!e.getStart().isAfter(Instant.now(clock))) {
            throw new BadRequestException("An event that has already started cannot be edited");
        }
    }

    private Event save(Event e) {
        e.setUpdatedAt(Instant.now(clock));
        return events.save(e);
    }

    private void log(Event e, String action, EventStatus from, EventStatus to, Requester who, String reason) {
        audit.save(new AuditLog(e.getId(), action, from, to, who.email(), reason));
    }

    private Event requireEvent(String id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private Club requireClub(String id) {
        return clubs.findById(id).orElseThrow(() -> new NotFoundException("Club not found"));
    }

    private static void requireOrganizer(Club club, Requester who) {
        if (who.role() != Role.SUPER_ADMIN && !club.isOrganizer(who.email())) {
            throw new ForbiddenException("Only this club's organizers can do that");
        }
    }

    private static void requireAdvisor(Club club, Requester who) {
        if (who.role() != Role.SUPER_ADMIN && !who.email().equals(club.getAdvisorEmail())) {
            throw new ForbiddenException("Only this club's faculty advisor can review its events");
        }
    }

    private static void requireManager(Requester who) {
        if (!who.isResourceManager()) {
            throw new ForbiddenException("Only resource admins can give the final approval");
        }
    }
}
