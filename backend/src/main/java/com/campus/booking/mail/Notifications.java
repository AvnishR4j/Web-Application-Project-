package com.campus.booking.mail;

import static com.campus.booking.mail.MailTemplates.details;
import static com.campus.booking.mail.MailTemplates.para;
import static com.campus.booking.mail.MailTemplates.row;

import com.campus.booking.mail.MailEvents.BookingDecided;
import com.campus.booking.mail.MailEvents.EventStatusChanged;
import com.campus.booking.mail.MailEvents.RegistrationChanged;
import com.campus.booking.mail.Mailer.Message;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Club;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.repository.ResourceRepository;
import com.campus.booking.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns what happened into emails. Listeners run only after the change is committed (a rolled-back retry sends
 * nothing) and on a background thread, so a slow email provider never slows the app down.
 */
@Component
public class Notifications {

    private static final Logger log = LoggerFactory.getLogger(Notifications.class);

    private final Mailer mailer;
    private final MailTemplates t;
    private final UnsubscribeTokens unsubscribe;
    private final EventRepository events;
    private final ClubRepository clubs;
    private final ResourceRepository resources;
    private final RegistrationRepository registrations;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final MongoTemplate mongo;

    public Notifications(Mailer mailer, MailTemplates t, UnsubscribeTokens unsubscribe, EventRepository events,
                         ClubRepository clubs, ResourceRepository resources, RegistrationRepository registrations,
                         BookingRepository bookings, UserRepository users, MongoTemplate mongo) {
        this.mailer = mailer;
        this.t = t;
        this.unsubscribe = unsubscribe;
        this.events = events;
        this.clubs = clubs;
        this.resources = resources;
        this.registrations = registrations;
        this.bookings = bookings;
        this.users = users;
        this.mongo = mongo;
    }

    // ----- registrations -----

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void on(RegistrationChanged e) {
        guard("registration", () -> {
            Event ev = events.findById(e.eventId()).orElse(null);
            if (ev == null) {
                return;
            }
            String heading;
            String text;
            switch (e.seat()) {
                case CONFIRMED -> {
                    heading = "You're in: " + ev.getTitle();
                    text = "Your place is confirmed. At the event, open Campus Board and enter the check-in code shown on the screen.";
                }
                case WAITLISTED -> {
                    heading = "You're on the waitlist: " + ev.getTitle();
                    text = "The event is full right now. If someone cancels, you move up automatically and we'll email you.";
                }
                default -> {
                    heading = "A seat opened up: " + ev.getTitle();
                    text = "Good news: you moved up from the waitlist and your place is now confirmed.";
                }
            }
            mailer.send(new Message(e.email(), heading,
                    t.page(heading, para(text) + eventDetails(ev), "See my registrations", t.link("/registrations"), null, null)));
        });
    }

    // ----- events -----

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void on(EventStatusChanged e) {
        guard("event", () -> {
            Event ev = events.findById(e.eventId()).orElse(null);
            if (ev == null) {
                return;
            }
            Club club = clubs.findById(ev.getClubId()).orElse(null);
            List<String> organizers = organizers(ev, club);
            switch (e.to()) {
                case SUBMITTED -> {
                    if (club != null && club.getAdvisorEmail() != null) {
                        String h = "Event to review: " + ev.getTitle();
                        mailer.send(new Message(club.getAdvisorEmail(), h, t.page(h,
                                para(clubName(club) + " submitted an event for your approval as faculty advisor.") + eventDetails(ev),
                                "Review it", t.link("/advisor"), null, null)));
                    }
                }
                case ADVISOR_APPROVED -> {
                    String h = "Advisor approved: " + ev.getTitle();
                    sendAll(organizers, h, t.page(h,
                            para("The faculty advisor approved your event. It now waits for the final approval from the resource admins.")
                                    + eventDetails(ev), "Open my events", t.link("/my-events"), null, null));
                    String h2 = "Event awaiting final approval: " + ev.getTitle();
                    sendAll(emailsOf(managers()), h2, t.page(h2,
                            para("The club's advisor has approved this event. It needs your final approval to confirm the venue.")
                                    + eventDetails(ev), "Review it", t.link("/event-approvals"), null, null));
                }
                case APPROVED -> {
                    String h = "Approved: " + ev.getTitle();
                    sendAll(organizers, h, t.page(h,
                            para("Your event is approved, the venue is confirmed, and students can now register.") + eventDetails(ev),
                            "Open my events", t.link("/my-events"), null, null));
                    if (e.from() == EventStatus.ADVISOR_APPROVED) {
                        announce(ev, club);
                    }
                }
                case REJECTED -> {
                    String h = "Not approved: " + ev.getTitle();
                    sendAll(organizers, h, t.page(h,
                            para("Your event was not approved.") + details(row("Reason", e.reason() == null ? "Not given" : e.reason()))
                                    + para("You can edit the event and submit it again."),
                            "Open my events", t.link("/my-events"), null, null));
                }
                case CANCELLED -> {
                    if (e.from() == EventStatus.APPROVED) {
                        List<String> attendees = registrations.findByEventIdInAndStatusIn(List.of(ev.getId()),
                                List.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED))
                                .stream().map((r) -> r.getUserEmail()).toList();
                        String h = "Cancelled: " + ev.getTitle();
                        String why = e.reason() == null ? "" : details(row("Reason", e.reason()));
                        sendAll(attendees, h, t.page(h, para("Sorry, this event has been cancelled by the organizers.") + why
                                + eventDetails(ev), "Browse other events", t.link("/events"), null, null));
                    }
                }
                default -> { }
            }
        });
    }

    /** "New event" email to every student who has not opted out, each with their own unsubscribe link. */
    private void announce(Event ev, Club club) {
        Query q = Query.query(Criteria.where("role").is(Role.STUDENT).and("eventEmails").ne(false));
        q.fields().include("email");
        List<User> students = mongo.find(q, User.class);
        String h = "New event: " + ev.getTitle();
        String intro = clubName(club) + " just announced an event.";
        String desc = ev.getDescription() == null || ev.getDescription().isBlank() ? "" : para(trim(ev.getDescription(), 400));
        List<Message> out = new ArrayList<>();
        for (User s : students) {
            if (!MailAddresses.deliverable(s.getEmail())) {
                continue;
            }
            String unsub = t.link("/unsubscribe?email=" + enc(s.getEmail()) + "&token=" + unsubscribe.sign(s.getEmail()));
            out.add(new Message(s.getEmail(), h, t.page(h, para(intro) + desc + eventDetails(ev),
                    "See it and register", t.link("/events"),
                    "You get these because you're a student on Campus Board.", unsub)));
        }
        if (!out.isEmpty()) {
            mailer.send(out);
        }
    }

    // ----- room bookings -----

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void on(BookingDecided e) {
        guard("booking", () -> {
            Booking b = bookings.findById(e.bookingId()).orElse(null);
            if (b == null || b.getEventId() != null) {
                return;
            }
            boolean ok = b.getStatus() == BookingStatus.APPROVED;
            String h = (ok ? "Booking approved: " : "Booking not approved: ") + b.getTitle();
            String body = para(ok ? "Your room is booked." : "Your booking request was not approved.")
                    + details(row("Room", roomName(b.getResourceId())), row("When", t.when(b.getStart(), b.getEnd())),
                    ok ? "" : row("Reason", b.getDecisionReason() == null ? "Not given" : b.getDecisionReason()));
            mailer.send(new Message(b.getRequesterEmail(), h, t.page(h, body, "My bookings", t.link("/bookings"), null, null)));
        });
    }

    // ----- reminders (called by EventReminders) -----

    public void remind(Event ev) {
        List<String> going = registrations.findByEventIdAndStatusOrderByConfirmedAtAsc(ev.getId(), RegistrationStatus.CONFIRMED)
                .stream().map((r) -> r.getUserEmail()).toList();
        String h = "Tomorrow: " + ev.getTitle();
        sendAll(going, h, t.page(h, para("A reminder that you're registered for this event. Check in with the code on the screen when you arrive.")
                + eventDetails(ev), "See my registrations", t.link("/registrations"),
                "Can't make it? Cancel in the app so someone on the waitlist gets your seat.", null));
    }

    // ----- helpers -----

    private String eventDetails(Event ev) {
        return details(row("When", t.when(ev.getStart(), ev.getEnd())), row("Where", roomName(ev.getResourceId())));
    }

    private void sendAll(Collection<String> to, String subject, String html) {
        Set<String> unique = new LinkedHashSet<>(to);
        List<Message> out = unique.stream().map((addr) -> new Message(addr, subject, html)).toList();
        if (!out.isEmpty()) {
            mailer.send(out);
        }
    }

    private List<String> organizers(Event ev, Club club) {
        Set<String> all = new LinkedHashSet<>();
        if (ev.getCreatedBy() != null) {
            all.add(ev.getCreatedBy());
        }
        if (club != null && club.getOrganizerEmails() != null) {
            all.addAll(club.getOrganizerEmails());
        }
        return List.copyOf(all);
    }

    private List<User> managers() {
        List<User> admins = users.findByRole(Role.RESOURCE_ADMIN);
        return admins.isEmpty() ? users.findByRole(Role.SUPER_ADMIN) : admins;
    }

    private static List<String> emailsOf(List<User> list) {
        return list.stream().map(User::getEmail).toList();
    }

    private String roomName(String resourceId) {
        return resources.findById(resourceId).map(Resource::getName).orElse("To be confirmed");
    }

    private static String clubName(Club club) {
        return club == null ? "A club" : club.getName();
    }

    private static String trim(String s, int max) {
        String one = s.strip();
        return one.length() <= max ? one : one.substring(0, max - 1).stripTrailing() + "…";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void guard(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            log.warn("Could not prepare {} email: {}", what, ex.toString());
        }
    }
}
